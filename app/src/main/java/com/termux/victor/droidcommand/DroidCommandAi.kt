package com.termux.zerocore.droidcommand

import com.termux.zerocore.config.VsLinks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.termux.R

/**
 * The AI option of the right drawer and Settings: hands off to the separate DroidCommand AI app
 * (Victorious93/droidcommand-ai). Launch only; DroidCommand AI is its own APK, so nothing is embedded.
 * If it is not installed, offers its download page instead of failing.
 */
object DroidCommandAi {
    /** Release package first, then the `.debug` build used during development. */
    private val PACKAGES = listOf("ai.droidcommand.app", "ai.droidcommand.app.debug")
    private const val RELEASES_URL = VsLinks.DROIDCOMMAND_RELEASES

    @JvmStatic
    fun launch(context: Context) {
        val launchIntent = PACKAGES.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent == null) {
            showNotInstalled(context)
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launchIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.droidcommand_ai_launch_failed, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, R.string.droidcommand_ai_launch_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showNotInstalled(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.droidcommand_ai_missing_title)
            .setMessage(R.string.droidcommand_ai_missing_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.droidcommand_ai_open_releases) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.droidcommand_ai_launch_failed, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
