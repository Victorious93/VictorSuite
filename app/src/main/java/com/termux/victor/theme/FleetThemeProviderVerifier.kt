package com.termux.victor.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build

/**
 * Checks that `content://com.victorious.launch.theme/current` is served by the real VictorLaunch:
 * the provider's owning package must be `com.victorious.launch` and its (single) signing cert
 * SHA-256 must match [FleetThemePins]. Any failure returns a reason; the client then uses the
 * built-in theme.
 */
internal object FleetThemeProviderVerifier {

    /** null when trusted, otherwise a short reason for the log. */
    fun untrustedReason(context: Context): String? {
        val pm = context.packageManager
        val info = try {
            pm.resolveContentProvider(FleetThemeContract.AUTHORITY, 0)
        } catch (e: Exception) {
            return "resolveContentProvider failed: ${e.javaClass.simpleName}"
        } ?: return "provider not installed"

        val owner = info.packageName
        if (owner != FleetThemeContract.PUBLISHER_PACKAGE) return "provider owned by unexpected package $owner"

        val signers = try {
            signerSha256(pm, owner)
        } catch (e: Exception) {
            return "could not read signer of $owner: ${e.javaClass.simpleName}"
        }
        if (!FleetThemePins.isTrustedPublisher(owner, signers)) return "signing cert pin mismatch for $owner"
        return null
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun signerSha256(pm: PackageManager, packageName: String): List<String> {
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pi: PackageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val si = pi.signingInfo ?: return emptyList()
            // Current signer(s) of the APK contents, not the rotation history.
            si.apkContentsSigners
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
        return sigs.orEmpty().map { FleetThemePins.sha256Hex(it.toByteArray()) }
    }
}
