package com.termux.zerocore.remotecontrol

/**
 * Anti-replay / freshness check (REMOTE_CONTROL.md §2).
 *
 * - `now > timestampMs + ttlMs`            -> `expired`
 * - `timestampMs > now + FUTURE_SKEW_MS`   -> `expired` (future skew only; past validity is TTL-only)
 * - seen `(sourceNodeRef, nonce)`          -> `replay_detected`
 *
 * Seen nonces are retained for at least [retentionMs] (>= 10 min) and at least
 * until the request itself could no longer be valid. In memory only (lost on
 * process death), same as the controller-side skeleton; not a MAC. The cache
 * fails closed (`internal`) instead of evicting live entries when full, and caps
 * each source so one paired app cannot exhaust the shared cache.
 */
class ReplayGuard(
    private val clock: RemoteClock = RemoteClock.SYSTEM,
    private val retentionMs: Long = FleetProtocol.REPLAY_RETENTION_MS,
    private val maxEntries: Int = 10_000,
    private val maxEntriesPerSource: Int = 1_000,
) {
    init {
        require(retentionMs >= FleetProtocol.REPLAY_RETENTION_MS) { "retention must be >= 10 minutes" }
        require(maxEntries > 0)
        require(maxEntriesPerSource > 0)
    }

    private data class Key(val sourceNodeRef: String, val nonce: String)

    /** key -> epoch ms after which the entry may be pruned. */
    private val seen = LinkedHashMap<Key, Long>()

    /** Validates freshness and records the nonce. Throws [RemoteControlException] on rejection. */
    @Synchronized
    fun checkAndRecord(sourceNodeRef: String, envelope: FleetEnvelope) {
        val now = clock.nowMs()
        checkFreshness(envelope.timestampMs, envelope.ttlMs, now)
        prune(now)
        val key = Key(sourceNodeRef, envelope.nonce.lowercase())
        if (seen.containsKey(key)) {
            throw RemoteControlException(RemoteErrorCategory.REPLAY_DETECTED, "nonce already seen for this source")
        }
        // A single paired source must not be able to fill the shared cache and lock out the others.
        if (seen.keys.count { it.sourceNodeRef == sourceNodeRef } >= maxEntriesPerSource) {
            throw RemoteControlException(RemoteErrorCategory.INTERNAL, "too many recent requests from this source; try again later")
        }
        if (seen.size >= maxEntries) {
            throw RemoteControlException(RemoteErrorCategory.INTERNAL, "replay cache full; try again later")
        }
        val validUntil = envelope.timestampMs + envelope.ttlMs
        seen[key] = maxOf(now + retentionMs, validUntil)
    }

    @Synchronized
    fun size(): Int = seen.size

    private fun prune(now: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value < now) it.remove()
        }
    }

    companion object {
        fun checkFreshness(timestampMs: Long, ttlMs: Long, now: Long) {
            if (ttlMs <= 0 || ttlMs > FleetProtocol.MAX_TTL_MS) invalid("ttlMs out of range")
            if (now > timestampMs + ttlMs) {
                throw RemoteControlException(RemoteErrorCategory.EXPIRED, "request expired (now > timestampMs + ttlMs)")
            }
            if (timestampMs > now + FleetProtocol.FUTURE_SKEW_MS) {
                throw RemoteControlException(RemoteErrorCategory.EXPIRED, "timestampMs is too far in the future (clock skew)")
            }
        }
    }
}
