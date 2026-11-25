package com.termux.victor.shellbroker

import java.security.MessageDigest

/**
 * VictorSuite shell broker contract (API version [BrokerApi.VERSION]).
 *
 * The broker is the single product entry point for fleet shell actions on this phone: interactive terminals and one-shot commands
 * requested by DroidCommand (for the VictorLinux fleet) or by other trusted callers. It is pure JVM so it can be unit tested; the
 * Android service, approval UI and pty live in `.android`.
 */
object BrokerApi {
    /** Bumped only for incompatible changes. A caller that needs a higher version than we report must refuse, not fall back to RUN_COMMAND. */
    const val VERSION = 1
    const val MIN_SUPPORTED_CALLER_VERSION = 1
    const val MAX_COMMAND_CHARS = 4096
    const val MAX_ONESHOT_OUTPUT_BYTES = 1 shl 20
    const val MAX_CHUNK_BYTES = 16 * 1024
}

enum class Mode(val wire: String) { INTERACTIVE("interactive"), ONESHOT("oneshot");
    companion object { fun of(s: String?) = values().firstOrNull { it.wire == s } } }

enum class Privilege(val wire: String) { USER("user"), ROOT("root");
    companion object { fun of(s: String?) = values().firstOrNull { it.wire == s } } }

/** What a trusted caller may do. Privileged (root) is a scope of its own and is never implied by the others. */
enum class Scope { TERMINAL, ONESHOT, PRIVILEGED, TRANSPORT_PROVIDER }

enum class SessionState(val wire: String, val final: Boolean = false) {
    AWAITING_APPROVAL("awaiting_approval"), OPEN("open"),
    DENIED("denied", true), CLOSED("closed", true), FAILED("failed", true), REVOKED("revoked", true), EXPIRED("expired", true)
}

/** Reasons a request is refused before the owner is even asked. Stable wire codes: callers show them and must not retry via another path. */
enum class RejectCode(val wire: String) {
    DISABLED("disabled"), UNKNOWN_CALLER("unknown_caller"), UNTRUSTED_CERT("untrusted_cert"), AMBIGUOUS_CALLER("ambiguous_caller"),
    SCOPE_NOT_GRANTED("scope_not_granted"), PRIVILEGE_NOT_GRANTED("privilege_not_granted"), BAD_REQUEST("bad_request"),
    BINDING_MISMATCH("binding_mismatch"), EXPIRED("expired"), DUPLICATE("duplicate"), BUSY("busy"), VERSION_UNSUPPORTED("version_unsupported"),
    NOT_OWNER("not_owner"), NO_SUCH_SESSION("no_such_session")
}

/**
 * One request. [peer] is who asked (shown to the owner verbatim and bound into [binding]); [target] is the node the shell runs on
 * (this phone's fleet device id, supplied by the caller and shown/bound, never trusted to widen anything).
 */
data class SessionRequest(
    val sessionId: String,
    val peer: String,
    val target: String,
    val mode: Mode,
    val privilege: Privilege,
    val command: String?,
    val cols: Int,
    val rows: Int,
    val nonce: String,
    val expiresMs: Long,
    val binding: String?,
    val callerApiVersion: Int = BrokerApi.VERSION,
)

/** The OS-authenticated identity of a caller: derived from the Binder uid, never from a request field. */
data class VerifiedCaller(val uid: Int, val packageName: String, val certSha256: String, val scopes: Set<Scope>, val label: String)

sealed class OpenAck {
    data class Accepted(val sessionId: String) : OpenAck()
    data class Rejected(val code: RejectCode, val reason: String) : OpenAck()
}

/** Everything a caller learns about a session. Output bytes are delivered here and nowhere else (no logs, no persistence). */
interface SessionSink {
    fun onState(sessionId: String, state: SessionState, reason: String?, binding: String?)
    fun onOutput(sessionId: String, data: ByteArray, channel: Channel)
    fun onExit(sessionId: String, status: Int)
}

enum class Channel(val wire: String) { TTY("tty"), STDOUT("stdout"), STDERR("stderr") }

/** Metadata-only audit trail. By construction it cannot carry commands' output or typed input. */
data class AuditEvent(val atMs: Long, val kind: String, val sessionId: String?, val caller: String?, val mode: Mode?, val privilege: Privilege?, val detail: String?)
fun interface BrokerAudit { fun record(e: AuditEvent) }

object Binding {
    /**
     * Byte-identical to `victorfleet.protocol.terminal_binding_hash` (VictorLinux fleet coordinator): sha256 over
     * "victorfleet-terminal-v1\n" + compact JSON (ensure_ascii) of [session, device, desktop, privilege, mode, command|null, nonce].
     * The owner's approval is for exactly these values; the phone echoes the hash and the coordinator compares it.
     */
    fun hash(r: SessionRequest): String = hash(r.sessionId, r.target, r.peer, r.privilege.wire, r.mode.wire, r.command, r.nonce)

    fun hash(session: String, device: String, desktop: String, privilege: String, mode: String, command: String?, nonce: String): String {
        val json = "[" + listOf(session, device, desktop, privilege, mode).joinToString(",") { pyString(it) } + "," +
            (command?.let { pyString(it) } ?: "null") + "," + pyString(nonce) + "]"
        val md = MessageDigest.getInstance("SHA-256").digest(("victorfleet-terminal-v1\n" + json).toByteArray(Charsets.UTF_8))
        return md.joinToString("") { "%02x".format(it) }
    }

    /** Python `json.dumps(s, ensure_ascii=True)` for one string. */
    internal fun pyString(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch == '\b' -> sb.append("\\b")
                ch == '\u000c' -> sb.append("\\f")
                ch.code < 0x20 || ch.code > 0x7e -> sb.append("\\u%04x".format(ch.code))
                else -> sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }
}
