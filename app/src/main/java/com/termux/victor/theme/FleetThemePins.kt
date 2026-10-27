package com.termux.victor.theme

import java.security.MessageDigest
import java.util.Locale

/**
 * Publisher pins for VictorLaunch, copied from the "VictorLaunch publisher pins" table in
 * FLEET_THEME.md. Accept current + next, always reject revoked. Blank / TBD slots do not
 * authorize, so `NEXT` and `RELEASE` are empty until the spec publishes a SHA-256.
 *
 * Pure JVM: no Android types, so it is unit-tested directly.
 */
object FleetThemePins {

    /** `com.victorious.launch` current (debug) signing cert SHA-256. */
    val CURRENT: List<String> = listOf(
        "B9:9A:AF:E2:C1:41:8B:5D:64:AA:0D:4B:AC:61:FC:F7:A7:BA:81:44:35:E0:EF:89:E8:AA:55:BE:56:4E:4A:69",
    )

    /** Next cert during a rotation. None published yet. */
    val NEXT: List<String> = emptyList()

    /** Release cert. TBD in the spec (no release key exists yet); do not invent one. */
    val RELEASE: List<String> = emptyList()

    /** Revoked: the old committed debug keystore. Never accepted. */
    val REVOKED: List<String> = listOf(
        "1B:06:A8:9E:7F:F5:BA:21:C9:A4:F8:F1:F0:4F:95:E5:62:78:A1:22:18:AD:37:33:28:CE:E7:83:1D:4F:BA:CA",
    )

    /** Uppercase hex without separators/whitespace, or null if it is not a 32-byte SHA-256. */
    fun normalize(fingerprint: String?): String? {
        if (fingerprint == null) return null
        val hex = fingerprint.replace(":", "").replace(" ", "").trim().uppercase(Locale.ROOT)
        if (hex.length != 64 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) return null
        return hex
    }

    fun sha256Hex(certBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append(String.format(Locale.ROOT, "%02X", b.toInt() and 0xFF))
        return sb.toString()
    }

    private fun normalizedSet(list: List<String>): Set<String> = list.mapNotNull { normalize(it) }.toSet()

    /**
     * True only if [packageName] is the publisher, the package has exactly one current signer,
     * and that signer is pinned (current or next) and not revoked. Multiple signers are refused
     * (conservative: every signer would have to be checked and the spec pins a single cert).
     */
    fun isTrustedPublisher(packageName: String?, signerSha256: List<String>): Boolean {
        if (packageName != FleetThemeContract.PUBLISHER_PACKAGE) return false
        if (signerSha256.size != 1) return false
        val signer = normalize(signerSha256[0]) ?: return false
        if (signer in normalizedSet(REVOKED)) return false
        val allowed = normalizedSet(CURRENT) + normalizedSet(NEXT) + normalizedSet(RELEASE)
        return signer in allowed
    }
}
