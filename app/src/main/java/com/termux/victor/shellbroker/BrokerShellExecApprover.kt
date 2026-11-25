package com.termux.victor.shellbroker

import com.termux.victor.remotecontrol.RemoteControlException
import com.termux.victor.remotecontrol.RemoteErrorCategory
import com.termux.victor.remotecontrol.ShellExecApprover
import com.termux.victor.remotecontrol.ShellExecResult
import com.termux.victor.remotecontrol.SourceIdentity
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Completes the `shell.exec` on-device confirmation flow: the envelope's `remote.command shell.exec` becomes a one-shot broker
 * session (same trust list, same owner approval dialog, same executor as every other fleet shell action) and returns the real
 * output and exit status. If the broker is off, the caller is untrusted, the owner declines or does not answer, the command does
 * NOT run and there is no fallback to any other execution path.
 */
class BrokerShellExecApprover(
    private val broker: ShellBroker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val approvalWindowMs: Long = 120_000,
    private val runWindowMs: Long = 300_000,
) : ShellExecApprover {
    override fun approveAndRun(caller: SourceIdentity, requestId: String, target: String, command: String): ShellExecResult {
        val uid = caller.uid ?: throw RemoteControlException(RemoteErrorCategory.UNAUTHORIZED, "caller uid could not be attested")
        val req = SessionRequest(
            sessionId = "env-$requestId".take(96), peer = "fleet:${caller.sourceNodeRef}", target = target, mode = Mode.ONESHOT, privilege = Privilege.USER,
            command = command, cols = 80, rows = 24, nonce = requestId, expiresMs = clock() + approvalWindowMs, binding = null,
        )
        val out = Collector()
        when (val ack = broker.open(uid, req, out)) {
            is OpenAck.Rejected -> throw when (ack.code) {
                RejectCode.DISABLED, RejectCode.PRIVILEGE_NOT_GRANTED, RejectCode.VERSION_UNSUPPORTED, RejectCode.BUSY ->
                    RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "VictorSuite refused: ${ack.reason}")
                RejectCode.BAD_REQUEST -> RemoteControlException(RemoteErrorCategory.INVALID_PARAMETERS, ack.reason)
                else -> RemoteControlException(RemoteErrorCategory.UNAUTHORIZED, "VictorSuite refused: ${ack.reason}")
            }
            is OpenAck.Accepted -> Unit
        }
        if (!out.done.await(approvalWindowMs + runWindowMs + 5_000, TimeUnit.MILLISECONDS)) {
            broker.close(uid, req.sessionId, "no result in time")
            throw RemoteControlException(RemoteErrorCategory.INTERNAL, "the command did not finish in time")
        }
        return when (out.finalState) {
            SessionState.CLOSED -> ShellExecResult(out.exit ?: throw RemoteControlException(RemoteErrorCategory.INTERNAL, "no exit status was reported"),
                out.stdout.toString(Charsets.UTF_8), out.stderr.toString(Charsets.UTF_8), out.truncated)
            SessionState.DENIED, SessionState.EXPIRED -> throw RemoteControlException(RemoteErrorCategory.CONFIRMATION_REQUIRED, "shell.exec was not approved on the device (${out.reason})")
            SessionState.REVOKED -> throw RemoteControlException(RemoteErrorCategory.UNAUTHORIZED, "access was revoked")
            else -> throw RemoteControlException(RemoteErrorCategory.INTERNAL, "the command could not be run (${out.reason})")
        }
    }

    private class Collector : SessionSink {
        val done = CountDownLatch(1)
        @Volatile var finalState: SessionState? = null
        @Volatile var reason: String? = null
        @Volatile var exit: Int? = null
        val stdout = ByteArrayOutputStream(); val stderr = ByteArrayOutputStream()
        @Volatile var truncated = false
        override fun onState(sessionId: String, state: SessionState, reason: String?, binding: String?) {
            if (state.final) { finalState = state; this.reason = reason; done.countDown() }
        }
        override fun onOutput(sessionId: String, data: ByteArray, channel: Channel) {
            val sink = if (channel == Channel.STDERR) stderr else stdout
            synchronized(sink) {
                val room = MAX_CAPTURE - sink.size()
                if (room <= 0) { truncated = true; return }
                sink.write(data, 0, minOf(room, data.size)); if (data.size > room) truncated = true
            }
        }
        override fun onExit(sessionId: String, status: Int) { exit = status }
    }

    private companion object { const val MAX_CAPTURE = 256 * 1024 }
}
