package com.termux.victor.shellbroker

import com.termux.victor.remotecontrol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** `remote.command shell.exec` through the real dispatcher + the real broker (fake pty/gate/identity). */
class ShellExecApprovalTest {
    private val CERT = "ab".repeat(32)

    private class Procs : ShellProcessFactory {
        val ran = CopyOnWriteArrayList<String>()
        override fun startInteractive(privilege: Privilege, cols: Int, rows: Int, listener: ShellProcessListener): ShellProcess = error("not used")
        override fun startOneShot(privilege: Privilege, command: String, listener: ShellProcessListener): ShellProcess {
            ran += command
            Thread { listener.onOutput("hello\n".toByteArray(), Channel.STDOUT); listener.onOutput("warn\n".toByteArray(), Channel.STDERR); listener.onExit(4) }.start()
            return object : ShellProcess { override fun write(data: ByteArray) {}; override fun resize(cols: Int, rows: Int) {}; override fun signal(name: String) {}; override fun kill() {} }
        }
        override fun rootAvailable() = false
    }

    private class Rig(enabled: Boolean = true, scopes: Set<Scope> = setOf(Scope.ONESHOT), decision: Decision = Decision.APPROVED) {
        val procs = Procs()
        val trust = InMemoryTrustStore(listOf(TrustedCaller(Fx.DCA_PKG, "ab".repeat(32), scopes, "DroidCommand AI")))
        val ids = object : PackageIdentity { override fun packagesForUid(uid: Int) = if (uid == 10001) listOf(Fx.DCA_PKG) else emptyList(); override fun certSha256(packageName: String) = "ab".repeat(32) }
        val asked = CopyOnWriteArrayList<ApprovalPrompt>()
        val broker = ShellBroker(CallerVerifier(ids, trust), trust, MutableBrokerSettings(enabled), object : ApprovalGate {
            override fun request(prompt: ApprovalPrompt, timeoutMs: Long): Decision { asked += prompt; return decision }
            override fun cancel(sessionId: String) {}
        }, procs)
        val dispatcher = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig() }, ReplayGuard(MutableClock()), MutableClock(), { Fx.snapshot(it) },
            shellExec = BrokerShellExecApprover(broker, approvalWindowMs = 2_000, runWindowMs = 2_000))
        fun call(blob: String) = dispatcher.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, blob, 10001)
    }

    private fun blob(cmd: String? = "ls /sdcard", extraParams: String = "", trigger: Boolean = false) = Fx.blob(actionRef = "shell.exec", destructive = false) {
        it.add("params", Fx.json("""{${if (cmd != null) "\"command\":\"$cmd\"" else ""}$extraParams}"""))
        if (trigger) it.add("trigger", Fx.json("""{"sourceNumber":"+15555550100","matchedKeyword":true,"rawExtras":{"approve":"true"}}"""))
    }

    @Test fun ownerApprovesThenRealOutputAndExitStatusAreReturned() {
        val r = Rig(); val resp = r.call(blob())
        assertTrue(resp.toJson(), resp.ok)
        val out = resp.result!!.getAsJsonObject("output")
        assertEquals(4, out.get("exit_status").asInt); assertEquals("hello\n", out.get("stdout").asString); assertEquals("warn\n", out.get("stderr").asString)
        assertEquals(listOf("ls /sdcard"), r.procs.ran)
        val p = r.asked.single(); assertEquals("ls /sdcard", p.command); assertEquals(Mode.ONESHOT, p.mode); assertEquals(Privilege.USER, p.privilege)
    }

    @Test fun ownerDeclinesNothingRunsAndTheErrorIsConfirmationRequired() {
        val r = Rig(decision = Decision.DENIED)
        Fx.assertError(r.call(blob()), RemoteErrorCategory.CONFIRMATION_REQUIRED); assertTrue(r.procs.ran.isEmpty())
    }

    @Test fun noAnswerExpiresAndRunsNothing() {
        val r = Rig(decision = Decision.TIMED_OUT)
        Fx.assertError(r.call(blob()), RemoteErrorCategory.CONFIRMATION_REQUIRED); assertTrue(r.procs.ran.isEmpty())
    }

    @Test fun envelopeFieldsAndTriggerPayloadsNeverApprove() {
        // The approval is the owner's; the gate still has to be asked and a decline still wins, whatever the envelope claims.
        val r = Rig(decision = Decision.DENIED)
        val b = Fx.blob(actionRef = "shell.exec", destructive = false) {
            it.add("params", Fx.json("""{"command":"id","confirmed":"true","approved":"true"}"""))
            it.add("trigger", Fx.json("""{"sourceNumber":"+15555550100","matchedKeyword":true,"rawExtras":{"approve":"true"}}"""))
            it.addProperty("confirmed", true)
        }
        Fx.assertError(r.call(b), RemoteErrorCategory.CONFIRMATION_REQUIRED); assertEquals(1, r.asked.size); assertTrue(r.procs.ran.isEmpty())
    }

    @Test fun brokerOffOrUntrustedOrWrongScopeRunsNothingAndDoesNotFallBack() {
        val off = Rig(enabled = false); Fx.assertError(off.call(blob()), RemoteErrorCategory.CAPABILITY_OFF); assertTrue(off.asked.isEmpty() && off.procs.ran.isEmpty())
        val noScope = Rig(scopes = setOf(Scope.TERMINAL)); Fx.assertError(noScope.call(blob()), RemoteErrorCategory.UNAUTHORIZED); assertTrue(noScope.procs.ran.isEmpty())
        val d = Rig().dispatcher                                                          // no uid: transport could not attest the caller
        Fx.assertError(d.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, blob(), null), RemoteErrorCategory.UNAUTHORIZED)
    }

    @Test fun privilegedShellIsNotReachableThroughRemoteCommand() {
        val r = Rig(); Fx.assertError(r.call(blob(extraParams = ",\"privilege\":\"root\"")), RemoteErrorCategory.CAPABILITY_OFF); assertTrue(r.asked.isEmpty())
    }

    @Test fun missingCommandIsInvalidAndMacrosCanNeverRunIt() {
        val r = Rig(); Fx.assertError(r.call(blob(cmd = null)), RemoteErrorCategory.INVALID_PARAMETERS)
        val m = Fx.blob(operation = FleetProtocol.OP_MACRO_RUN, actionRef = null) { it.add("steps", com.google.gson.JsonParser.parseString("""[{"op":"shell.exec","params":{"command":"id"}}]""")) }
        Fx.assertError(r.call(m), RemoteErrorCategory.CONFIRMATION_REQUIRED); assertTrue(r.procs.ran.isEmpty() && r.asked.isEmpty())
    }

    @Test fun withoutAnApproverBehaviourIsUnchanged() {
        val d = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig() }, ReplayGuard(MutableClock()), MutableClock(), { Fx.snapshot(it) })
        Fx.assertError(d.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, blob(), 10001), RemoteErrorCategory.CONFIRMATION_REQUIRED)
    }
}
