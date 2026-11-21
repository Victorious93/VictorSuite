package com.termux.victor.remotecontrol

import java.security.MessageDigest

/**
 * On-device approval for a remote one-shot shell command (`shell.exec`).
 *
 * The remote caller can only *propose*. Nothing here runs until the phone's owner approves that exact proposal
 * (peer + target + argv + privilege are bound by [ShellApproval.commandSha256] and shown on the approval screen).
 * An approval is single use. Privileged runs (root / Shizuku) need a separate owner grant per privilege and are
 * never implied by an ordinary approval. Revoking a peer cancels its pending and running commands.
 *
 * Pure JVM. The Android layer supplies the [ShellRunner] (VictorSuite's own terminal runtime) and the approval UI.
 */
enum class ShellPrivilege(val wire: String) {
    USER("user"), ROOT("root"), SHIZUKU("shizuku");

    companion object { fun fromWire(v: String?): ShellPrivilege? = entries.firstOrNull { it.wire == v } }
}

data class ShellRunResult(val exitCode: Int, val stdout: String, val stderr: String, val timedOut: Boolean = false)

/** Runs argv exactly (no shell interpretation unless argv itself is `sh -c ...`, which the owner sees). */
fun interface ShellRunner {
    fun run(argv: List<String>, privilege: ShellPrivilege, timeoutMs: Long, cancelled: () -> Boolean): ShellRunResult
}

enum class ShellApprovalState { PENDING, DENIED, EXPIRED, RUNNING, DONE, REVOKED }

data class ShellApproval(
    val id: String,
    val peer: String,                 // authenticated sourceNodeRef, never from the payload
    val peerPackage: String?,
    val target: String,               // the one node this approval is for
    val argv: List<String>,
    val privilege: ShellPrivilege,
    val commandSha256: String,
    val createdMs: Long,
    val expiresMs: Long,
    val state: ShellApprovalState,
    val result: ShellRunResult? = null,
)

class ShellApprovalQueue(
    private val clock: RemoteClock = RemoteClock.SYSTEM,
    private val runner: ShellRunner,
    /** Privileges the owner has switched on in settings. USER is always available once the owner approves a request. */
    private val grantedPrivileges: () -> Set<ShellPrivilege> = { emptySet() },
    private val nodeId: String,
    private val ttlMs: Long = 120_000,
    private val maxPending: Int = 8,
    private val maxOutputChars: Int = 64 * 1024,
    private val timeoutMs: Long = 60_000,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val items = LinkedHashMap<String, ShellApproval>()
    private val cancelFlags = HashMap<String, Boolean>()

    /** Remote proposal. Never runs anything. */
    fun submit(caller: SourceIdentity, target: String, argv: List<String>, privilege: ShellPrivilege): ShellApproval {
        if (target != nodeId) throw RemoteControlException(RemoteErrorCategory.NOT_FOUND, "target '$target' is not this node")
        validate(argv)
        if (privilege != ShellPrivilege.USER && privilege !in grantedPrivileges())
            throw RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "${privilege.wire} shell access is not enabled on this device")
        synchronized(lock) {
            expireLocked()
            if (items.values.count { it.state == ShellApprovalState.PENDING } >= maxPending)
                throw RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "too many pending approvals")
            val now = clock.nowMs()
            val a = ShellApproval(newId(), caller.sourceNodeRef, caller.packageName, target, argv, privilege,
                sha(caller.sourceNodeRef, target, argv, privilege), now, now + ttlMs, ShellApprovalState.PENDING)
            items[a.id] = a
            return a
        }
    }

    fun pending(): List<ShellApproval> = synchronized(lock) { expireLocked(); items.values.filter { it.state == ShellApprovalState.PENDING } }

    /**
     * Owner approved. [shownSha256] is the hash of what the approval screen displayed; if the proposal differs (it
     * cannot, but a UI bug must not turn into a different command), nothing runs. Runs once, on the caller's thread.
     */
    fun approve(id: String, shownSha256: String): ShellApproval {
        val a: ShellApproval
        synchronized(lock) {
            expireLocked()
            a = items[id] ?: throw RemoteControlException(RemoteErrorCategory.NOT_FOUND, "no such approval")
            if (a.state != ShellApprovalState.PENDING) throw RemoteControlException(RemoteErrorCategory.EXPIRED, "approval is ${a.state.name.lowercase()}")
            if (!MessageDigest.isEqual(a.commandSha256.toByteArray(), shownSha256.toByteArray()))
                throw RemoteControlException(RemoteErrorCategory.INVALID_PARAMETERS, "approval does not match the command shown")
            if (a.privilege != ShellPrivilege.USER && a.privilege !in grantedPrivileges()) {
                items[id] = a.copy(state = ShellApprovalState.DENIED); throw RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "privilege was switched off")
            }
            items[id] = a.copy(state = ShellApprovalState.RUNNING)
            cancelFlags[id] = false
        }
        val result = try {
            runner.run(a.argv, a.privilege, timeoutMs) { synchronized(lock) { cancelFlags[id] == true } }
        } catch (t: Throwable) { ShellRunResult(-1, "", "runner error: ${t.javaClass.simpleName}") }
        synchronized(lock) {
            val cur = items[id]!!
            val trimmed = result.copy(stdout = result.stdout.take(maxOutputChars), stderr = result.stderr.take(maxOutputChars))
            val done = if (cur.state == ShellApprovalState.REVOKED) cur else cur.copy(state = ShellApprovalState.DONE, result = trimmed)
            items[id] = done; cancelFlags.remove(id)
            return done
        }
    }

    fun deny(id: String) = synchronized(lock) {
        items[id]?.takeIf { it.state == ShellApprovalState.PENDING }?.let { items[id] = it.copy(state = ShellApprovalState.DENIED) }
        Unit
    }

    /** Peer revoked or unpaired: pending proposals die, running commands are cancelled, results are withheld. */
    fun revokePeer(peer: String): Int = synchronized(lock) {
        var n = 0
        for ((id, a) in items.entries.toList()) if (a.peer == peer && a.state in setOf(ShellApprovalState.PENDING, ShellApprovalState.RUNNING)) {
            if (a.state == ShellApprovalState.RUNNING) cancelFlags[id] = true
            items[id] = a.copy(state = ShellApprovalState.REVOKED, result = null); n++
        }
        n
    }

    /** Only the peer that proposed the command can read its state and output. */
    fun result(id: String, caller: SourceIdentity): ShellApproval? = synchronized(lock) {
        expireLocked()
        items[id]?.takeIf { it.peer == caller.sourceNodeRef }
    }

    private fun expireLocked() {
        val now = clock.nowMs()
        for ((id, a) in items.entries.toList()) if (a.state == ShellApprovalState.PENDING && now > a.expiresMs) items[id] = a.copy(state = ShellApprovalState.EXPIRED)
    }

    private fun validate(argv: List<String>) {
        if (argv.isEmpty() || argv.size > 64) invalid("argv must have 1..64 entries")
        if (argv[0].isEmpty() || argv.any { it.length > 4096 || '\u0000' in it }) invalid("argv entry invalid")
    }

    companion object {
        fun sha(peer: String, target: String, argv: List<String>, privilege: ShellPrivilege): String {
            val md = MessageDigest.getInstance("SHA-256")
            fun f(s: String) { md.update(s.length.toString().toByteArray()); md.update(0); md.update(s.toByteArray()); md.update(0) }
            f(peer); f(target); f(privilege.wire); argv.forEach(::f)
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
