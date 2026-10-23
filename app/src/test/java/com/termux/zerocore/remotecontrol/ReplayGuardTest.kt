package com.termux.zerocore.remotecontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGuardTest {
    private fun env(nonce: String, ts: Long, ttl: Long? = null) = FleetEnvelopeCodec.parse(Fx.blob(nonce = nonce, timestampMs = ts, ttlMs = ttl))

    @Test
    fun duplicateNonceFromSameSourceIsReplay() {
        val clock = MutableClock()
        val g = ReplayGuard(clock)
        val n = Fx.nonce()
        g.checkAndRecord("controller", env(n, clock.now))
        Fx.expectCategory(RemoteErrorCategory.REPLAY_DETECTED) { g.checkAndRecord("controller", env(n, clock.now)) }
    }

    @Test
    fun nonceIsCaseInsensitiveForReplay() {
        val clock = MutableClock()
        val g = ReplayGuard(clock)
        g.checkAndRecord("controller", env("ABCDEF0123456789ABCDEF0123456789", clock.now))
        Fx.expectCategory(RemoteErrorCategory.REPLAY_DETECTED) { g.checkAndRecord("controller", env("abcdef0123456789abcdef0123456789", clock.now)) }
    }

    @Test
    fun sameNonceFromDifferentSourceIsDistinctKey() {
        val clock = MutableClock()
        val g = ReplayGuard(clock)
        val n = Fx.nonce()
        g.checkAndRecord("controller", env(n, clock.now))
        g.checkAndRecord("victorsms-" + "0".repeat(32), env(n, clock.now))
        assertEquals(2, g.size())
    }

    @Test
    fun expiredWhenNowPastTimestampPlusTtl() {
        val clock = MutableClock()
        val g = ReplayGuard(clock)
        val ts = clock.now - 120_001
        Fx.expectCategory(RemoteErrorCategory.EXPIRED) { g.checkAndRecord("controller", env(Fx.nonce(), ts)) }
    }

    @Test
    fun exactlyAtTtlBoundaryIsStillValid() {
        val clock = MutableClock()
        ReplayGuard(clock).checkAndRecord("controller", env(Fx.nonce(), clock.now - 120_000))
    }

    @Test
    fun futureSkewBeyond120sIsExpired() {
        val clock = MutableClock()
        val g = ReplayGuard(clock)
        Fx.expectCategory(RemoteErrorCategory.EXPIRED) { g.checkAndRecord("controller", env(Fx.nonce(), clock.now + 120_001)) }
        g.checkAndRecord("controller", env(Fx.nonce(), clock.now + 120_000))
    }

    @Test
    fun pastValidityIsTtlOnlyPerFrozenSpec() {
        // REMOTE_CONTROL.md §2: timestampMs = now - 200000 with ttlMs = 300000 is valid.
        val clock = MutableClock()
        ReplayGuard(clock).checkAndRecord("controller", env(Fx.nonce(), clock.now - 200_000, 300_000))
    }

    @Test
    fun seenNoncesAreRetainedAtLeastTenMinutes() {
        val clock = MutableClock()
        val g = ReplayGuard(clock)
        val t0 = clock.now
        g.checkAndRecord("controller", env(Fx.nonce(), t0))
        clock.now = t0 + 9 * 60_000
        g.checkAndRecord("controller", env(Fx.nonce(), clock.now)) // triggers prune
        assertEquals(2, g.size())
        clock.now = t0 + 10 * 60_000 + 1
        g.checkAndRecord("controller", env(Fx.nonce(), clock.now))
        assertEquals(2, g.size()) // first entry pruned only after 10 minutes
    }

    @Test
    fun retentionBelowTenMinutesIsRejected() {
        try {
            ReplayGuard(MutableClock(), retentionMs = 60_000)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun fullCacheFailsClosed() {
        val clock = MutableClock()
        val g = ReplayGuard(clock, maxEntries = 1)
        g.checkAndRecord("controller", env(Fx.nonce(), clock.now))
        Fx.expectCategory(RemoteErrorCategory.INTERNAL) { g.checkAndRecord("controller", env(Fx.nonce(), clock.now)) }
    }

    @Test
    fun oneSourceCannotExhaustTheSharedCache() {
        val clock = MutableClock()
        val g = ReplayGuard(clock, maxEntries = 4, maxEntriesPerSource = 2)
        g.checkAndRecord("noisy", env(Fx.nonce(), clock.now))
        g.checkAndRecord("noisy", env(Fx.nonce(), clock.now))
        Fx.expectCategory(RemoteErrorCategory.INTERNAL) { g.checkAndRecord("noisy", env(Fx.nonce(), clock.now)) }
        // Other sources are unaffected.
        g.checkAndRecord("controller", env(Fx.nonce(), clock.now))
        g.checkAndRecord("victorlaunch", env(Fx.nonce(), clock.now))
        assertEquals(4, g.size())
    }
}
