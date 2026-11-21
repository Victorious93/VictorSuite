package com.termux.victor.remotecontrol

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellApprovalTest {
    private val clock = MutableClock()
    private val ran = ArrayList<Pair<List<String>, ShellPrivilege>>()
    private var granted: Set<ShellPrivilege> = emptySet()
    private var runnerResult = ShellRunResult(0, "out\n", "")
    private val queue = ShellApprovalQueue(clock, { argv, priv, _, _ -> ran += argv to priv; runnerResult }, { granted }, Fx.NODE_ID)
    private val dispatcher = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig() }, ReplayGuard(clock), clock, { Fx.snapshot(it) }, shellQueue = queue)
    private val caller = SourceIdentity(Fx.DCA_REF, Fx.DCA_PKG, Fx.DCA_CERT)

    private fun shell(argv: String, extra: Map<String, String> = emptyMap(), target: String = Fx.NODE_ID) =
        dispatcher.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(actionRef = "shell.exec") {
            it.add("params", JsonObject().apply { addProperty("argv", argv); addProperty("target", target); extra.forEach { (k, v) -> addProperty(k, v) } })
        })

    private fun approvalId(r: RemoteResponse): String = Fx.json(r.toJson()).getAsJsonObject("error").getAsJsonObject("details").get("approvalId").asString

    @Test fun `a remote proposal never runs and reports confirmation_required with an approval id`() {
        val r = shell("""["id"]""")
        Fx.assertError(r, RemoteErrorCategory.CONFIRMATION_REQUIRED)
        assertTrue(approvalId(r).isNotBlank()); assertTrue(ran.isEmpty())
        assertEquals(1, queue.pending().size)
    }

    @Test fun `owner approval runs it once and only the proposing peer can read output and exit status`() {
        val id = approvalId(shell("""["echo","hi"]"""))
        val shown = queue.pending().single().commandSha256
        val done = queue.approve(id, shown)
        assertEquals(ShellApprovalState.DONE, done.state); assertEquals(listOf(listOf("echo", "hi") to ShellPrivilege.USER), ran)
        val res = dispatcher.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(actionRef = "command.result") { it.add("params", JsonObject().apply { addProperty("approvalId", id) }) })
        assertTrue(res.toJson(), res.ok)
        val o = res.result!!
        assertEquals("done", o.get("state").asString); assertEquals(0, o.get("exitCode").asInt); assertEquals("out\n", o.get("stdout").asString)
        val other = SourceIdentity("someone-else", "com.other", "ZZ")
        assertNull(queue.result(id, other))
        Fx.expectCategory(RemoteErrorCategory.EXPIRED) { queue.approve(id, shown) }          // already DONE: cannot run again
    }

    @Test fun `approval is single use`() {
        val id = approvalId(shell("""["id"]""")); val sha = queue.pending().single().commandSha256
        queue.approve(id, sha)
        Fx.expectCategory(RemoteErrorCategory.EXPIRED) { queue.approve(id, sha) }
        assertEquals(1, ran.size)
    }

    @Test fun `approval is bound to the exact command shown`() {
        val id = approvalId(shell("""["echo","one"]"""))
        val wrong = ShellApprovalQueue.sha(Fx.DCA_REF, Fx.NODE_ID, listOf("echo", "two"), ShellPrivilege.USER)
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { queue.approve(id, wrong) }
        assertTrue(ran.isEmpty()); assertEquals(ShellApprovalState.PENDING, queue.pending().single().state)
    }

    @Test fun `denied and expired proposals never run`() {
        val a = approvalId(shell("""["a"]""")); val sha = queue.pending().single().commandSha256
        queue.deny(a)
        Fx.expectCategory(RemoteErrorCategory.EXPIRED) { queue.approve(a, sha) }
        val b = approvalId(shell("""["b"]""")); val shaB = queue.pending().single().commandSha256
        clock.now += 10 * 60 * 1000
        Fx.expectCategory(RemoteErrorCategory.EXPIRED) { queue.approve(b, shaB) }
        assertTrue(ran.isEmpty())
    }

    @Test fun `root needs its own owner grant and an ordinary approval never implies it`() {
        Fx.assertError(shell("""["id"]""", mapOf("privilege" to "root")), RemoteErrorCategory.CAPABILITY_OFF)
        granted = setOf(ShellPrivilege.ROOT)
        val id = approvalId(shell("""["id"]""", mapOf("privilege" to "root")))
        granted = emptySet()                                                       // owner switches it off before approving
        Fx.expectCategory(RemoteErrorCategory.CAPABILITY_OFF) { queue.approve(id, queue.pending().single().commandSha256) }
        assertTrue(ran.isEmpty())
        granted = setOf(ShellPrivilege.ROOT)
        val id2 = approvalId(shell("""["id"]""", mapOf("privilege" to "root")))
        queue.approve(id2, queue.pending().single { it.id == id2 }.commandSha256)
        assertEquals(ShellPrivilege.ROOT, ran.single().second)
    }

    @Test fun `target must be this node and must be named`() {
        Fx.assertError(shell("""["id"]""", target = "some-other-node"), RemoteErrorCategory.NOT_FOUND)
        val noTarget = dispatcher.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(actionRef = "shell.exec") { it.add("params", JsonObject().apply { addProperty("argv", """["id"]""") }) })
        Fx.assertError(noTarget, RemoteErrorCategory.INVALID_PARAMETERS)
    }

    @Test fun `revoking the peer cancels pending and running commands and withholds output`() {
        val id = approvalId(shell("""["sleep","9"]"""))
        assertEquals(1, queue.revokePeer(Fx.DCA_REF))
        Fx.expectCategory(RemoteErrorCategory.EXPIRED) { queue.approve(id, ShellApprovalQueue.sha(Fx.DCA_REF, Fx.NODE_ID, listOf("sleep", "9"), ShellPrivilege.USER)) }
        // running: runner sees the cancel flag and the final state stays REVOKED with no result
        var q2: ShellApprovalQueue? = null
        q2 = ShellApprovalQueue(clock, { _, _, _, cancelled -> q2!!.revokePeer(Fx.DCA_REF); ShellRunResult(if (cancelled()) 137 else 0, "secret", "") }, { emptySet() }, Fx.NODE_ID)
        val a = q2.submit(caller, Fx.NODE_ID, listOf("x"), ShellPrivilege.USER)
        val fin = q2.approve(a.id, a.commandSha256)
        assertEquals(ShellApprovalState.REVOKED, fin.state); assertNull(fin.result)
    }

    @Test fun `macros cannot carry shell commands`() {
        val r = dispatcher.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(operation = "macro.run", actionRef = null) {
            it.add("steps", com.google.gson.JsonParser.parseString("""[{"op":"shell.exec","params":{"argv":"[\"id\"]","target":"${Fx.NODE_ID}"}}]"""))
        })
        Fx.assertError(r, RemoteErrorCategory.CONFIRMATION_REQUIRED)
        assertTrue(queue.pending().isEmpty() && ran.isEmpty())
    }

    @Test fun `unpaired or external-control-off callers cannot even propose`() {
        val unpaired = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig() }, ReplayGuard(clock), clock, { Fx.snapshot(it) }, shellQueue = queue)
        Fx.assertError(unpaired.handleRemote("com.evil", "AA:BB:CC:DD", Fx.blob(actionRef = "shell.exec")), RemoteErrorCategory.NOT_PAIRED)
        val off = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig(externalControl = false) }, ReplayGuard(clock), clock, { Fx.snapshot(it) }, shellQueue = queue)
        Fx.assertError(off.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(actionRef = "shell.exec")), RemoteErrorCategory.EXTERNAL_CONTROL_DISABLED)
        assertTrue(queue.pending().isEmpty())
    }

    @Test fun `without a queue shell exec stays blocked exactly as before`() {
        val legacy = Fx.dispatcher()
        Fx.assertError(legacy.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(actionRef = "shell.exec")), RemoteErrorCategory.CONFIRMATION_REQUIRED)
    }

    @Test fun `argv validation`() {
        for (bad in listOf("[]", """[""]""", """["a\u0000b"]""", "not json", """[1]""")) Fx.assertError(shell(bad), RemoteErrorCategory.INVALID_PARAMETERS)
        assertFalse(queue.pending().isNotEmpty())
    }
}
