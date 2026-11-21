package com.termux.victor.shellbroker

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** What the owner is asked. Everything here is shown verbatim and bound by [binding]. */
data class ApprovalPrompt(
    val sessionId: String, val callerLabel: String, val callerPackage: String, val peer: String, val target: String,
    val mode: Mode, val privilege: Privilege, val command: String?, val expiresMs: Long, val binding: String,
)

enum class Decision { APPROVED, DENIED, TIMED_OUT }

/** The on-device approval. Android implementation: a notification + a full-screen confirmation activity that only a tap by the owner resolves. */
interface ApprovalGate {
    /** Blocks (on the broker's worker thread) until the owner decides, [timeoutMs] elapses or [cancel] is called for this session. */
    fun request(prompt: ApprovalPrompt, timeoutMs: Long): Decision
    fun cancel(sessionId: String)
}

/** A running shell: a real pseudoterminal (interactive) or a plain process (one-shot). */
interface ShellProcess {
    fun write(data: ByteArray)
    fun resize(cols: Int, rows: Int)
    fun signal(name: String)
    fun kill()
}


interface ShellProcessListener {
    fun onOutput(data: ByteArray, channel: Channel)
    fun onExit(status: Int)
}

interface ShellProcessFactory {
    /** Starts an interactive login shell in a pty for [privilege]. Throws when it cannot (reported as FAILED, never as success). */
    fun startInteractive(privilege: Privilege, cols: Int, rows: Int, listener: ShellProcessListener): ShellProcess
    /** Runs [command] once (stdin closed), reporting stdout/stderr separately and the real exit status. */
    fun startOneShot(privilege: Privilege, command: String, listener: ShellProcessListener): ShellProcess
    fun rootAvailable(): Boolean
}

/** Owner-controlled switches. Everything defaults to OFF. */
interface BrokerSettings {
    fun enabled(): Boolean
    fun rootAllowed(): Boolean
}

class MutableBrokerSettings(var enabledFlag: Boolean = false, var rootFlag: Boolean = false) : BrokerSettings {
    override fun enabled() = enabledFlag
    override fun rootAllowed() = rootFlag
}

data class BrokerTimeouts(val approvalMs: Long = 120_000, val oneShotMs: Long = 300_000, val maxSessions: Int = 4)

class ShellBroker(
    private val verifier: CallerVerifier,
    private val trust: TrustStore,
    private val settings: BrokerSettings,
    private val gate: ApprovalGate,
    private val processes: ShellProcessFactory,
    private val audit: BrokerAudit = BrokerAudit { },
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeouts: BrokerTimeouts = BrokerTimeouts(),
    private val workers: ExecutorService = Executors.newCachedThreadPool { r -> Thread(r, "ShellBroker").apply { isDaemon = true } },
) {
    private class Session(val req: SessionRequest, val caller: VerifiedCaller, val sink: SessionSink) {
        @Volatile var state = SessionState.AWAITING_APPROVAL
        @Volatile var process: ShellProcess? = null
        @Volatile var exitStatus: Int? = null
        val lock = Any()
        var bytesOut = 0
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val seen = LinkedHashSet<String>()                 // bounded replay window of session ids

    init {
        trust.addListener { enforceTrust() }
    }

    fun capabilities(): Map<String, Any> = mapOf(
        "api_version" to BrokerApi.VERSION, "enabled" to settings.enabled(), "root_allowed" to settings.rootAllowed(),
        "root_available" to (settings.rootAllowed() && processes.rootAvailable()),
    )

    private fun ev(kind: String, s: Session?, detail: String? = null) =
        audit.record(AuditEvent(clock(), kind, s?.req?.sessionId, s?.caller?.packageName, s?.req?.mode, s?.req?.privilege, detail))

    // ------------------------------------------------------------------------------------------------ open
    fun open(callerUid: Int, req: SessionRequest, sink: SessionSink): OpenAck {
        if (req.callerApiVersion < BrokerApi.MIN_SUPPORTED_CALLER_VERSION || req.callerApiVersion > BrokerApi.VERSION)
            return reject(null, RejectCode.VERSION_UNSUPPORTED, "broker API ${BrokerApi.VERSION} does not serve caller API ${req.callerApiVersion}")
        if (!settings.enabled()) return reject(null, RejectCode.DISABLED, "remote shell access is switched off in VictorSuite")
        val need = when {
            req.privilege == Privilege.ROOT -> Scope.PRIVILEGED
            req.mode == Mode.ONESHOT -> Scope.ONESHOT
            else -> Scope.TERMINAL
        }
        val caller = when (val v = verifier.verify(callerUid, need)) {
            is Verification.Rejected -> return reject(null, v.code, v.reason)
            is Verification.Ok -> v.caller
        }
        // Privileged also needs the matching ordinary scope: it adds to, never replaces, ordinary shell access.
        if (req.privilege == Privilege.ROOT) {
            val base = if (req.mode == Mode.ONESHOT) Scope.ONESHOT else Scope.TERMINAL
            if (base !in caller.scopes) return reject(caller, RejectCode.SCOPE_NOT_GRANTED, "'${caller.packageName}' was not granted ${base.name.lowercase()} access")
            if (!settings.rootAllowed()) return reject(caller, RejectCode.PRIVILEGE_NOT_GRANTED, "privileged shells are switched off in VictorSuite")
            if (!processes.rootAvailable()) return reject(caller, RejectCode.PRIVILEGE_NOT_GRANTED, "root is not available on this phone")
        }
        if (req.sessionId.isBlank() || req.sessionId.length > 96 || req.peer.isBlank() || req.peer.length > 128 || req.target.isBlank() || req.nonce.isBlank())
            return reject(caller, RejectCode.BAD_REQUEST, "missing or oversized request fields")
        if (req.cols !in 1..1000 || req.rows !in 1..1000) return reject(caller, RejectCode.BAD_REQUEST, "bad terminal size")
        when (req.mode) {
            Mode.ONESHOT -> if (req.command.isNullOrBlank() || req.command.length > BrokerApi.MAX_COMMAND_CHARS)
                return reject(caller, RejectCode.BAD_REQUEST, "a one-shot needs a command up to ${BrokerApi.MAX_COMMAND_CHARS} chars")
            Mode.INTERACTIVE -> if (req.command != null) return reject(caller, RejectCode.BAD_REQUEST, "interactive sessions take no preset command")
        }
        val binding = Binding.hash(req)
        if (req.binding != null && req.binding != binding)
            return reject(caller, RejectCode.BINDING_MISMATCH, "the request's approval binding does not match its contents")
        val now = clock()
        if (req.expiresMs <= now) return reject(caller, RejectCode.EXPIRED, "the request has expired")
        val s = Session(req, caller, sink)
        synchronized(seen) {
            if (!seen.add(req.sessionId)) return reject(caller, RejectCode.DUPLICATE, "session id was already used (requests are never replayed)")
            if (seen.size > 512) seen.remove(seen.first())
        }
        synchronized(sessions) {
            if (sessions.values.count { !it.state.final } >= timeouts.maxSessions) return reject(caller, RejectCode.BUSY, "too many open shell sessions")
            sessions[req.sessionId] = s
        }
        ev("requested", s, "${req.mode.wire}/${req.privilege.wire}")
        sink.onState(req.sessionId, SessionState.AWAITING_APPROVAL, null, null)
        workers.execute { runApproved(s, binding) }
        return OpenAck.Accepted(req.sessionId)
    }

    private fun reject(c: VerifiedCaller?, code: RejectCode, why: String): OpenAck {
        audit.record(AuditEvent(clock(), "rejected", null, c?.packageName, null, null, code.wire))
        return OpenAck.Rejected(code, why)
    }

    private fun runApproved(s: Session, binding: String) {
        val req = s.req
        val wait = minOf(timeouts.approvalMs, (req.expiresMs - clock()).coerceAtLeast(1))
        val prompt = ApprovalPrompt(req.sessionId, s.caller.label, s.caller.packageName, req.peer, req.target, req.mode, req.privilege, req.command, req.expiresMs, binding)
        val decision = try { gate.request(prompt, wait) } catch (t: Throwable) { Decision.DENIED }
        synchronized(s.lock) {
            if (s.state.final) return                         // revoked/closed while the owner was deciding
            // Re-check trust at the moment of approval: a revocation during the dialog must win.
            if (!stillAllowed(s)) { finish(s, SessionState.REVOKED, "access was revoked while waiting for approval"); return }
            when (decision) {
                Decision.DENIED -> { finish(s, SessionState.DENIED, "the owner declined"); return }
                Decision.TIMED_OUT -> { finish(s, SessionState.EXPIRED, "the owner did not respond in time"); return }
                Decision.APPROVED -> Unit
            }
            s.state = SessionState.OPEN                       // announce `open` (with the binding) BEFORE any process output exists
            ev("approved", s)
            s.sink.onState(req.sessionId, SessionState.OPEN, null, binding)
        }
        val listener = object : ShellProcessListener {
            override fun onOutput(data: ByteArray, channel: Channel) {
                synchronized(s.lock) {
                    if (s.state != SessionState.OPEN) return
                    if (req.mode == Mode.ONESHOT) {
                        s.bytesOut += data.size
                        if (s.bytesOut > BrokerApi.MAX_ONESHOT_OUTPUT_BYTES) { s.process?.kill(); finish(s, SessionState.FAILED, "output limit reached"); return }
                    }
                    var off = 0
                    while (off < data.size) {
                        val n = minOf(BrokerApi.MAX_CHUNK_BYTES, data.size - off)
                        s.sink.onOutput(req.sessionId, data.copyOfRange(off, off + n), channel); off += n
                    }
                }
            }
            override fun onExit(status: Int) {
                synchronized(s.lock) {
                    if (s.state.final) return
                    s.exitStatus = status
                    s.sink.onExit(req.sessionId, status)
                    finish(s, SessionState.CLOSED, if (req.mode == Mode.ONESHOT) "command finished" else "shell exited")
                }
            }
        }
        try {
            val p = if (req.mode == Mode.INTERACTIVE) processes.startInteractive(req.privilege, req.cols, req.rows, listener)
            else processes.startOneShot(req.privilege, req.command!!, listener)
            synchronized(s.lock) {
                s.process = p
                if (s.state.final) p.kill()                     // revoked between approval and start
            }
            if (req.mode == Mode.ONESHOT) workers.execute {
                Thread.sleep(timeouts.oneShotMs)
                synchronized(s.lock) { if (!s.state.final) { s.process?.kill(); finish(s, SessionState.EXPIRED, "one-shot time limit") } }
            }
        } catch (t: Throwable) {
            synchronized(s.lock) { if (!s.state.final) finish(s, SessionState.FAILED, "could not start the shell: ${t.javaClass.simpleName}") }
        }
    }

    /** Caller must still be trusted for the scope this session used. */
    private fun stillAllowed(s: Session): Boolean {
        if (!settings.enabled()) return false
        val row = trust.find(s.caller.packageName) ?: return false
        if (row.certSha256.replace(":", "").lowercase() != s.caller.certSha256) return false
        val base = if (s.req.mode == Mode.ONESHOT) Scope.ONESHOT else Scope.TERMINAL
        if (base !in row.scopes) return false
        if (s.req.privilege == Privilege.ROOT && (Scope.PRIVILEGED !in row.scopes || !settings.rootAllowed())) return false
        return true
    }

    private fun finish(s: Session, st: SessionState, why: String?) {
        synchronized(s.lock) {
            if (s.state.final) return
            s.state = st
            gate.cancel(s.req.sessionId)
            if (st != SessionState.CLOSED) s.process?.kill()
            ev(st.wire, s, why)
            s.sink.onState(s.req.sessionId, st, why, null)
        }
    }

    // ------------------------------------------------------------------------------- live session calls
    private fun owned(uid: Int, id: String): Pair<Session?, RejectCode?> {
        val s = sessions[id] ?: return null to RejectCode.NO_SUCH_SESSION
        if (s.caller.uid != uid) return null to RejectCode.NOT_OWNER                  // another app cannot drive this shell
        return s to null
    }

    fun input(uid: Int, id: String, data: ByteArray): RejectCode? {
        val (s, err) = owned(uid, id); if (s == null) return err
        synchronized(s.lock) {
            if (s.state != SessionState.OPEN || s.req.mode != Mode.INTERACTIVE) return RejectCode.NO_SUCH_SESSION   // nothing queued, nothing replayed
            if (!stillAllowed(s)) { finish(s, SessionState.REVOKED, "access was revoked"); return RejectCode.NO_SUCH_SESSION }
            s.process?.write(data)
        }
        return null
    }

    fun resize(uid: Int, id: String, cols: Int, rows: Int): RejectCode? {
        val (s, err) = owned(uid, id); if (s == null) return err
        if (cols !in 1..1000 || rows !in 1..1000) return RejectCode.BAD_REQUEST
        synchronized(s.lock) { if (s.state == SessionState.OPEN) s.process?.resize(cols, rows) }
        return null
    }

    fun signal(uid: Int, id: String, name: String): RejectCode? {
        val (s, err) = owned(uid, id); if (s == null) return err
        synchronized(s.lock) { if (s.state == SessionState.OPEN) s.process?.signal(name) }
        return null
    }

    fun close(uid: Int, id: String, reason: String): RejectCode? {
        val (s, err) = owned(uid, id); if (s == null) return err
        finish(s, SessionState.CLOSED, reason)
        return null
    }

    // --------------------------------------------------------------------------------------- revocation
    /** Owner pressed Revoke / disconnected: ends the caller's live sessions at once. */
    fun revokeCaller(packageName: String, why: String = "revoked by the owner") {
        sessions.values.filter { it.caller.packageName == packageName && !it.state.final }.forEach { finish(it, SessionState.REVOKED, why) }
    }

    fun revokeAll(why: String = "all shell access was revoked") {
        sessions.values.filter { !it.state.final }.forEach { finish(it, SessionState.REVOKED, why) }
    }

    /** Re-evaluates every live session against the current trust list and settings (called on any change). */
    fun enforceTrust() {
        sessions.values.filter { !it.state.final && !stillAllowed(it) }.forEach { finish(it, SessionState.REVOKED, "access was revoked") }
    }

    fun liveSessions(): List<Triple<String, String, SessionState>> =
        sessions.values.filter { !it.state.final }.map { Triple(it.req.sessionId, it.caller.packageName, it.state) }
}
