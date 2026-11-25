package com.termux.victor.shellbroker.android

import android.content.Context
import com.termux.shared.android.PackageUtils
import com.termux.victor.shellbroker.PackageIdentity

/** OS-attested package identity: uid → packages via PackageManager, certificate via the package's signing info. */
class AndroidPackageIdentity(private val context: Context) : PackageIdentity {
    override fun packagesForUid(uid: Int): List<String> = context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
    override fun certSha256(packageName: String): String? = try {
        PackageUtils.getSigningCertificateSHA256DigestForPackage(context, packageName)?.replace(":", "")?.lowercase()
    } catch (e: Exception) { null }
}
