package com.termux.victor.shellbroker

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShellBrokerTest {
    private val CERT = "ab".repeat(32)
    private val NOW = 1_790_000_000_000L

    class Ids(val map: Map<Int, List<String>>, val certs: Map<String, String?>) : PackageIdentity {
        override fun packagesForUid(uid: Int) = map[uid].orEmpty()
        override fun certSha256(packageName: String) = certs[packageName]
    }

    class FakeProc(val l: ShellProcessListener, val oneShot: Boolean, val privilege: Privilege) : ShellProcess {
        val written = CopyOnWriteArrayList<ByteArray>(); val sizes = CopyOnWriteArrayList<Pair<Int, Int>>(); val signals = CopyOnWriteArrayList<String>()
        @Volatile var killed = false
        override fun write(data: ByteArray) { written += data }
        override fun resize(cols: Int, rows: Int) { sizes += cols to rows }
        override fun signal(name: String) { signals += name }
        override fun kill() { killed = true }
    }

    class Procs(var root: Boolean = false) : ShellProcessFactory {
        val started = CopyOnWriteArrayList<FakeProc>()
        var failStart = false
        override fun startInteractive(privilege: Privilege, cols: Int, rows: Int, listener: ShellProcessListener): ShellProcess {
            if (failStart) error("no pty"); return FakeProc(listener, false, privilege).also { started += it }
        }
        override fun startOneShot(privilege: Privilege, command: String, listener: ShellProcessListener): ShellProcess {
            if (failStart) error("no exec"); return FakeProc(listener, true, privilege).also { started += it }
        }
        override fun rootAvailable() = root
    }

    class Gate(var decision: Decision = Decision.APPROVED, val block: CountDownLatch? = null) : ApprovalGate {
        val prompts = CopyOnWriteArrayList<ApprovalPrompt>()
        override fun request(prompt: ApprovalPrompt, timeoutMs: Long): Decision { prompts += prompt; block?.await(5, TimeUnit.SECONDS); return decision }
        override fun cancel(sessionId: String) {}
    }

    class Sink : SessionSink {
        val states = CopyOnWriteArrayList<Pair<SessionState, String?>>(); val bindings = CopyOnWriteArrayList<String?>()
        val out = CopyOnWriteArrayList<Triple<String, Channel, ByteArray>>(); @Volatile var exit: Int? = null
        override fun onState(sessionId: String, state: SessionState, reason: String?, binding: String?) { states += state to reason; bindings += binding }
        override fun onOutput(sessionId: String, data: ByteArray, channel: Channel) { out += Triple(sessionId, channel, data) }
        override fun onExit(sessionId: String, status: Int) { exit = status }
        fun awaitState(s: SessionState, ms: Long = 3000): Boolean { val end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { if (states.any { it.first == s }) return true; Thread.sleep(5) }; return false }
        fun text() = out.joinToString("") { String(it.third) }
    }

    private class Rig(val cert: String, scopes: Set<Scope>, enabled: Boolean = true, root: Boolean = false, rootProc: Boolean = false, gate: Gate = Gate(), t: BrokerTimeouts = BrokerTimeouts()) {
        val trust = InMemoryTrustStore(listOf(TrustedCaller("ai.droidcommand.app", cert, scopes, "DroidCommand AI")))
        val ids = Ids(mapOf(10001 to listOf("ai.droidcommand.app"), 10002 to listOf("evil.app"), 10003 to listOf("ai.droidcommand.app", "other.app")),
            mapOf("ai.droidcommand.app" to cert, "evil.app" to "cd".repeat(32), "other.app" to cert))
        val settings = MutableBrokerSettings(enabled, root)
        val procs = Procs(rootProc)
        val audit = CopyOnWriteArrayList<AuditEvent>()
        val gate = gate
        var now = 1_790_000_000_000L
        val broker = ShellBroker(CallerVerifier(ids, trust), trust, settings, gate, procs, { audit += it }, { now }, t)
    }

    private fun req(id: String = "term-1", mode: Mode = Mode.INTERACTIVE, priv: Privilege = Privilege.USER, command: String? = null, expires: Long = NOW + 120_000, binding: Boolean = true): SessionRequest {
        val base = SessionRequest(id, "victorlinux:owner@host", "dev-aa", mode, priv, if (mode == Mode.ONESHOT) (command ?: "ls") else null, 100, 30, "nonce-$id", expires, null)
        return if (binding) base.copy(binding = Binding.hash(base)) else base
    }


    // ----------------------------------------------------------------------------------- contract vectors
    @Test fun bindingMatchesTheCoordinatorFixtures() {
        for (f in listOf("terminal_open_interactive", "terminal_open_oneshot")) {
            val p = JsonParser.parseString(javaClass.getResource("/fleet-fixtures/$f.json")!!.readText()).asJsonObject
            val pl = p.getAsJsonObject("payload")
            val r = SessionRequest(pl["session_id"].asString, pl["desktop"].asString, p["target"].asString, Mode.of(pl["mode"].asString)!!, Privilege.of(pl["privilege"].asString)!!,
                pl.get("command")?.takeIf { !it.isJsonNull }?.asString, pl["cols"].asInt, pl["rows"].asInt, pl["nonce"].asString, pl["expires_ms"].asLong, pl["binding"].asString)
            assertEquals(f, pl["binding"].asString, Binding.hash(r))
        }
    }

    @Test fun bindingMatchesPythonForUnicodeAndControlCharacters() {
        assertEquals("f64d73f27af4640887cfff64a0c1f5db3e3d230631f9fbe3409300c4c24b6c70", Binding.hash("term-ü-1", "dev-1", "victorlinux:o\"w\\n@host\n\t", "user", "oneshot", "echo 'héllo 😀' > /tmp/x\u0001\u007f", "n "))
    }

    // --------------------------------------------------------------------------------------- admission
    @Test fun offByDefault() {
        val r = Rig(CERT, setOf(Scope.TERMINAL), enabled = false)
        val a = r.broker.open(10001, req(), Sink())
        assertEquals(RejectCode.DISABLED, (a as OpenAck.Rejected).code); assertTrue(r.gate.prompts.isEmpty())
    }

    @Test fun unknownWrongCertAmbiguousAndMissingScopeAreRefusedBeforeAsking() {
        val r = Rig(CERT, setOf(Scope.TERMINAL))
        assertEquals(RejectCode.UNKNOWN_CALLER, (r.broker.open(99999, req(), Sink()) as OpenAck.Rejected).code)
        assertEquals(RejectCode.UNKNOWN_CALLER, (r.broker.open(10002, req(), Sink()) as OpenAck.Rejected).code)          // not in trust list
        r.trust.upsert(TrustedCaller("evil.app", CERT, setOf(Scope.TERMINAL)))                                             // listed, but cert differs
        assertEquals(RejectCode.UNTRUSTED_CERT, (r.broker.open(10002, req("t2"), Sink()) as OpenAck.Rejected).code)
        assertEquals(RejectCode.AMBIGUOUS_CALLER, (r.broker.open(10003, req("t3"), Sink()) as OpenAck.Rejected).code)     // shared uid
        assertEquals(RejectCode.SCOPE_NOT_GRANTED, (r.broker.open(10001, req("t4", Mode.ONESHOT), Sink()) as OpenAck.Rejected).code)  // terminal != oneshot
        assertTrue(r.gate.prompts.isEmpty()); assertTrue(r.procs.started.isEmpty())
    }

    @Test fun olderCallerApiOrLegacyDowngradeIsRefusedNotServedViaAnotherPath() {
        val r = Rig(CERT, setOf(Scope.TERMINAL))
        val a = r.broker.open(10001, req().copy(callerApiVersion = BrokerApi.VERSION + 1), Sink())
        assertEquals(RejectCode.VERSION_UNSUPPORTED, (a as OpenAck.Rejected).code)
    }

    @Test fun rootIsAGrantOfItsOwnAndNeedsEverySwitch() {
        val a = Rig(CERT, setOf(Scope.TERMINAL), root = true, rootProc = true)
        assertEquals(RejectCode.PRIVILEGE_NOT_GRANTED, (a.broker.open(10001, req(priv = Privilege.ROOT), Sink()) as OpenAck.Rejected).code)   // scope missing
        val b = Rig(CERT, setOf(Scope.TERMINAL, Scope.PRIVILEGED), root = false, rootProc = true)
        assertEquals(RejectCode.PRIVILEGE_NOT_GRANTED, (b.broker.open(10001, req(priv = Privilege.ROOT), Sink()) as OpenAck.Rejected).code)   // switch off
        val c = Rig(CERT, setOf(Scope.TERMINAL, Scope.PRIVILEGED), root = true, rootProc = false)
        assertEquals(RejectCode.PRIVILEGE_NOT_GRANTED, (c.broker.open(10001, req(priv = Privilege.ROOT), Sink()) as OpenAck.Rejected).code)   // no root
        val d = Rig(CERT, setOf(Scope.PRIVILEGED), root = true, rootProc = true)
        assertEquals(RejectCode.SCOPE_NOT_GRANTED, (d.broker.open(10001, req(priv = Privilege.ROOT), Sink()) as OpenAck.Rejected).code)      // root alone is not ordinary access
        val e = Rig(CERT, setOf(Scope.TERMINAL, Scope.PRIVILEGED), root = true, rootProc = true); val s = Sink()
        assertTrue(e.broker.open(10001, req(priv = Privilege.ROOT), s) is OpenAck.Accepted); assertTrue(s.awaitState(SessionState.OPEN))
        assertEquals(Privilege.ROOT, e.gate.prompts.single().privilege)                                                                        // the owner is told it is root
    }

    @Test fun tamperedBindingExpiredAndReplayedRequestsAreRefused() {
        val r = Rig(CERT, setOf(Scope.TERMINAL))
        assertEquals(RejectCode.BINDING_MISMATCH, (r.broker.open(10001, req().copy(binding = "0".repeat(64)), Sink()) as OpenAck.Rejected).code)
        assertEquals(RejectCode.EXPIRED, (r.broker.open(10001, req("old", expires = NOW - 1), Sink()) as OpenAck.Rejected).code)
        val s = Sink(); assertTrue(r.broker.open(10001, req("once"), s) is OpenAck.Accepted); assertTrue(s.awaitState(SessionState.OPEN))
        assertEquals(RejectCode.DUPLICATE, (r.broker.open(10001, req("once"), Sink()) as OpenAck.Rejected).code)
    }

    @Test fun interactiveWithPresetCommandOrOneShotWithoutOneIsRefused() {
        val r = Rig(CERT, setOf(Scope.TERMINAL, Scope.ONESHOT))
        assertEquals(RejectCode.BAD_REQUEST, (r.broker.open(10001, req("a").copy(command = "rm -rf ~", binding = null), Sink()) as OpenAck.Rejected).code)
        assertEquals(RejectCode.BAD_REQUEST, (r.broker.open(10001, req("b", Mode.ONESHOT).copy(command = " ", binding = null), Sink()) as OpenAck.Rejected).code)
    }

    // -------------------------------------------------------------------------------------- the session
    @Test fun ownerApprovalShowsEverythingTheBindingCovers() {
        val r = Rig(CERT, setOf(Scope.ONESHOT)); val s = Sink()
        r.broker.open(10001, req("o1", Mode.ONESHOT, command = "ls -l /sdcard"), s); assertTrue(s.awaitState(SessionState.OPEN))
        val p = r.gate.prompts.single()
        assertEquals("victorlinux:owner@host", p.peer); assertEquals("dev-aa", p.target); assertEquals("ls -l /sdcard", p.command)
        assertEquals(Mode.ONESHOT, p.mode); assertEquals(Privilege.USER, p.privilege); assertEquals("DroidCommand AI", p.callerLabel); assertEquals("ai.droidcommand.app", p.callerPackage)
        assertEquals(Binding.hash(req("o1", Mode.ONESHOT, command = "ls -l /sdcard")), p.binding)
        assertEquals(p.binding, s.bindings.last { it != null })                                                                    // echoed with `open`
    }

    @Test fun deniedNeverStartsAProcess() {
        val r = Rig(CERT, setOf(Scope.TERMINAL), gate = Gate(Decision.DENIED)); val s = Sink()
        r.broker.open(10001, req(), s); assertTrue(s.awaitState(SessionState.DENIED))
        assertTrue(r.procs.started.isEmpty()); assertNull(s.exit)
        assertEquals(RejectCode.NO_SUCH_SESSION, r.broker.input(10001, "term-1", "echo x\n".toByteArray()))
    }

    @Test fun approvalTimeoutExpires() {
        val r = Rig(CERT, setOf(Scope.TERMINAL), gate = Gate(Decision.TIMED_OUT)); val s = Sink()
        r.broker.open(10001, req(), s); assertTrue(s.awaitState(SessionState.EXPIRED)); assertTrue(r.procs.started.isEmpty())
    }

    @Test fun fullInteractiveLifecycle() {
        val r = Rig(CERT, setOf(Scope.TERMINAL)); val s = Sink()
        r.broker.open(10001, req(), s); assertTrue(s.awaitState(SessionState.OPEN))
        val p = r.procs.started.single()
        assertNull(r.broker.input(10001, "term-1", "ls\n".toByteArray())); assertEquals("ls\n", String(p.written.single()))
        assertNull(r.broker.resize(10001, "term-1", 120, 40)); assertEquals(120 to 40, p.sizes.single())
        assertNull(r.broker.signal(10001, "term-1", "INT")); assertEquals("INT", p.signals.single())
        p.l.onOutput("marker.txt\n".toByteArray(), Channel.TTY); assertEquals("marker.txt\n", s.text())
        p.l.onExit(7); assertEquals(7, s.exit); assertTrue(s.awaitState(SessionState.CLOSED))
        assertEquals(RejectCode.NO_SUCH_SESSION, r.broker.input(10001, "term-1", "late\n".toByteArray()))                              // closed: never delivered
        assertEquals(1, p.written.size)
    }

    @Test fun anotherAppCannotDriveTheSession() {
        val r = Rig(CERT, setOf(Scope.TERMINAL)); val s = Sink()
        r.trust.upsert(TrustedCaller("evil.app", "cd".repeat(32), setOf(Scope.TERMINAL)))
        r.broker.open(10001, req(), s); assertTrue(s.awaitState(SessionState.OPEN))
        assertEquals(RejectCode.NOT_OWNER, r.broker.input(10002, "term-1", "x".toByteArray()))
        assertEquals(RejectCode.NOT_OWNER, r.broker.close(10002, "term-1", "x"))
        assertTrue(r.procs.started.single().written.isEmpty())
    }

    @Test fun revokingTheCallerEndsTheLiveShellAtOnceAndBlocksFurtherInput() {
        val r = Rig(CERT, setOf(Scope.TERMINAL)); val s = Sink()
        r.broker.open(10001, req(), s); assertTrue(s.awaitState(SessionState.OPEN))
        r.trust.remove("ai.droidcommand.app")
        assertTrue(s.awaitState(SessionState.REVOKED)); assertTrue(r.procs.started.single().killed)
        assertEquals(RejectCode.NO_SUCH_SESSION, r.broker.input(10001, "term-1", "x".toByteArray()))
    }

    @Test fun switchingBrokerOffOrDroppingScopeEndsSessions() {
        val r = Rig(CERT, setOf(Scope.TERMINAL, Scope.ONESHOT)); val s = Sink()
        r.broker.open(10001, req(), s); assertTrue(s.awaitState(SessionState.OPEN))
        r.settings.enabledFlag = false; r.broker.enforceTrust()
        assertTrue(s.awaitState(SessionState.REVOKED)); assertTrue(r.procs.started.single().killed)
        r.settings.enabledFlag = true
        val s2 = Sink(); r.broker.open(10001, req("t2"), s2); assertTrue(s2.awaitState(SessionState.OPEN))
        r.trust.upsert(TrustedCaller("ai.droidcommand.app", CERT, setOf(Scope.ONESHOT)))                    // terminal scope removed
        assertTrue(s2.awaitState(SessionState.REVOKED))
    }

    @Test fun revocationDuringTheApprovalDialogWinsOverApproval() {
        val latch = CountDownLatch(1); val r = Rig(CERT, setOf(Scope.TERMINAL), gate = Gate(Decision.APPROVED, latch)); val s = Sink()
        r.broker.open(10001, req(), s)
        while (r.gate.prompts.isEmpty()) Thread.sleep(5)
        r.trust.remove("ai.droidcommand.app")
        assertTrue(s.awaitState(SessionState.REVOKED)); latch.countDown(); Thread.sleep(100)
        assertTrue(r.procs.started.isEmpty())                                                              // approved afterwards, but nothing ran
        assertFalse(s.states.any { it.first == SessionState.OPEN })
    }

    @Test fun oneShotReportsStdoutStderrAndRealExitStatus() {
        val r = Rig(CERT, setOf(Scope.ONESHOT)); val s = Sink()
        r.broker.open(10001, req("os", Mode.ONESHOT, command = "ls; echo err >&2; exit 3"), s); assertTrue(s.awaitState(SessionState.OPEN))
        val p = r.procs.started.single(); assertTrue(p.oneShot)
        p.l.onOutput("a.txt\n".toByteArray(), Channel.STDOUT); p.l.onOutput("err\n".toByteArray(), Channel.STDERR); p.l.onExit(3)
        assertEquals(3, s.exit); assertEquals(setOf(Channel.STDOUT, Channel.STDERR), s.out.map { it.second }.toSet())
        assertEquals(RejectCode.NO_SUCH_SESSION, r.broker.input(10001, "os", "x".toByteArray()))            // a one-shot takes no live input
    }

    @Test fun oneShotOutputIsBoundedAndTheProcessKilled() {
        val r = Rig(CERT, setOf(Scope.ONESHOT)); val s = Sink()
        r.broker.open(10001, req("big", Mode.ONESHOT, command = "yes"), s); assertTrue(s.awaitState(SessionState.OPEN))
        val p = r.procs.started.single(); val blob = ByteArray(300 * 1024)
        repeat(5) { p.l.onOutput(blob, Channel.STDOUT) }
        assertTrue(s.awaitState(SessionState.FAILED)); assertTrue(p.killed)
    }

    @Test fun startFailureIsReportedAsFailureNotSuccess() {
        val r = Rig(CERT, setOf(Scope.TERMINAL)); r.procs.failStart = true; val s = Sink()
        r.broker.open(10001, req(), s); assertTrue(s.awaitState(SessionState.FAILED)); assertNull(s.exit)
    }

    @Test fun auditNeverCarriesContentsOrTypedInput() {
        val r = Rig(CERT, setOf(Scope.TERMINAL)); val s = Sink()
        r.broker.open(10001, req(), s); assertTrue(s.awaitState(SessionState.OPEN))
        r.broker.input(10001, "term-1", "SECRET-KEYSTROKES".toByteArray()); r.procs.started.single().l.onOutput("SECRET-OUTPUT".toByteArray(), Channel.TTY)
        r.broker.close(10001, "term-1", "done")
        val all = r.audit.joinToString("\n") { "${it.kind} ${it.sessionId} ${it.caller} ${it.detail}" }
        assertFalse(all.contains("SECRET")); assertTrue(all.contains("approved")); assertTrue(all.contains("closed"))
    }

    @Test fun capacityIsBounded() {
        val r = Rig(CERT, setOf(Scope.TERMINAL), t = BrokerTimeouts(maxSessions = 1)); val s = Sink()
        r.broker.open(10001, req("a"), s); assertTrue(s.awaitState(SessionState.OPEN))
        assertEquals(RejectCode.BUSY, (r.broker.open(10001, req("b"), Sink()) as OpenAck.Rejected).code)
    }
}

class TransportProviderTrustTest {
    private val cert = "ab".repeat(32)
    private fun verifier(scopes: Set<Scope>, certOnDevice: String? = cert): CallerVerifier {
        val trust = InMemoryTrustStore(listOf(TrustedCaller(RustDeskVTransport.PACKAGE, cert, scopes, "rustdesk")))
        val ids = object : PackageIdentity { override fun packagesForUid(uid: Int) = emptyList<String>(); override fun certSha256(packageName: String) = certOnDevice }
        return CallerVerifier(ids, trust)
    }

    @Test fun trustedProviderWithScopeIsAccepted() = assertTrue(verifier(setOf(Scope.TRANSPORT_PROVIDER)).verifyProvider(RustDeskVTransport.PACKAGE) is Verification.Ok)
    @Test fun rebrandedPackageAndServiceMatchRustDeskV() {
        assertEquals("com.victorious.rustdeskv", RustDeskVTransport.PACKAGE)
        assertEquals("com.carriez.flutter_hbb.VictorTerminalTransportService", RustDeskVTransport.SERVICE)
    }
    @Test fun oldStockPackageDoesNotInheritRustDeskVTrust() =
        assertEquals(RejectCode.UNKNOWN_CALLER, (verifier(setOf(Scope.TRANSPORT_PROVIDER)).verifyProvider("com.carriez.flutter_hbb") as Verification.Rejected).code)
    @Test fun unknownPackageRefused() = assertEquals(RejectCode.UNKNOWN_CALLER, (verifier(setOf(Scope.TRANSPORT_PROVIDER)).verifyProvider("evil.rustdesk") as Verification.Rejected).code)
    @Test fun repackagedProviderWithDifferentCertificateRefused() =
        assertEquals(RejectCode.UNTRUSTED_CERT, (verifier(setOf(Scope.TRANSPORT_PROVIDER), "cd".repeat(32)).verifyProvider(RustDeskVTransport.PACKAGE) as Verification.Rejected).code)
    @Test fun terminalScopeAloneDoesNotMakeAnAppATransport() =
        assertEquals(RejectCode.SCOPE_NOT_GRANTED, (verifier(setOf(Scope.TERMINAL, Scope.ONESHOT)).verifyProvider(RustDeskVTransport.PACKAGE) as Verification.Rejected).code)
}
