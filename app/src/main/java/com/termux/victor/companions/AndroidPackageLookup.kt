package com.termux.victor.companions

import android.content.Context
import android.content.pm.PackageManager

/**
 * [PackageLookup] backed by the real PackageManager. On Android 11+ the
 * companion packages must be visible to VictorSuite (a `<queries>` entry or
 * the shared user id); when they are not, they simply read as not installed.
 */
class AndroidPackageLookup(private val context: Context) : PackageLookup {
    private val pm: PackageManager get() = context.packageManager

    @Suppress("DEPRECATION")
    override fun installedVersion(packageId: String): String? = try {
        pm.getPackageInfo(packageId, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    override fun signatureMatchesSelf(packageId: String): Boolean = try {
        pm.checkSignatures(context.packageName, packageId) == PackageManager.SIGNATURE_MATCH
    } catch (e: Exception) {
        false
    }
}
