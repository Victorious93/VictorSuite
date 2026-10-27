package com.termux.victor.hak5

import com.termux.victor.config.VsLinks

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Deep-link handoff to RootForge for Plunder Bug capture (plan D3).
 * VictorSuite has NO USB code — this only launches RootForge or offers sideload.
 *
 * Handoff action per docs/SUITE_PLAN.md: ai.rootforge.os.action.OPEN with
 * destination extra. Falls back to MAIN launch intent, then GitHub releases.
 */
object RootForgeHandoff {
    const val PACKAGE = "ai.rootforge.os"
    const val PACKAGE_DEBUG = "ai.rootforge.os.debug"
    const val ACTION_OPEN = "ai.rootforge.os.action.OPEN"
    const val EXTRA_DESTINATION = "destination"
    const val DESTINATION_PLUNDER_BUG = "plunder_bug"
    const val SIDELOAD_URL = VsLinks.ROOTFORGE_RELEASES

    data class ResolveResult(
        val installed: Boolean,
        val packageName: String?,
        val intent: Intent?
    )

    fun resolve(context: Context, destination: String = DESTINATION_PLUNDER_BUG): ResolveResult {
        val pm = context.packageManager
        for (pkg in listOf(PACKAGE, PACKAGE_DEBUG)) {
            if (!isPackageInstalled(pm, pkg)) continue
            val open = Intent(ACTION_OPEN).apply {
                setPackage(pkg)
                putExtra(EXTRA_DESTINATION, destination)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (open.resolveActivity(pm) != null) {
                return ResolveResult(installed = true, packageName = pkg, intent = open)
            }
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launch.putExtra(EXTRA_DESTINATION, destination)
                return ResolveResult(installed = true, packageName = pkg, intent = launch)
            }
        }
        return ResolveResult(installed = false, packageName = null, intent = null)
    }

    fun sideloadIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(SIDELOAD_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
