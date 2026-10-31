package com.termux.victor.config.mainmenu.config

import com.termux.victor.config.VsLinks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.termux.victor.util.UUtils
import com.termux.R
import com.termux.victor.config.mainmenu.MainMenuConfig

/**
 * Drawer entry that hands off to the separate Victorious ANDRAX app (Victorious93/victorious-andrax,
 * package `com.victorious.andrax`, launcher activity `MainActivity`). Same pattern as
 * [RootForgeOpenClickConfig]: launch only, nothing embedded. See docs/PLUGIN_CONTRACT.md.
 *
 * Tries the package's launcher intent first, then the explicit component. If neither resolves,
 * offers the download page instead of failing.
 */
class AndraxOpenClickConfig : BaseMenuClickConfig() {
    override fun getType(): Int {
        return MainMenuConfig.CODE_SUITE
    }

    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.link_ico)
    }

    override fun getString(context: Context?): String? {
        return context?.getString(R.string.andrax_open_title) ?: "Open Victorious ANDRAX"
    }

    override fun onClick(view: View?, context: Context?) {
        if (context == null) return
        val pm = context.packageManager
        val launchIntent = pickLaunchIntent(
            launcherIntent = pm.getLaunchIntentForPackage(PACKAGE),
            explicitIntent = explicitIntent(),
            resolves = { it.resolveActivity(pm) != null }
        )
        if (launchIntent == null) {
            showNotInstalled(context)
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launchIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.andrax_launch_failed, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, R.string.andrax_launch_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showNotInstalled(context: Context) {
        val dialog = switchDialogShow(
            context.getString(R.string.andrax_missing_title),
            context.getString(R.string.andrax_missing_msg),
            context
        )
        dialog.ok?.text = UUtils.getString(R.string.andrax_open_releases)
        dialog.cancel?.setOnClickListener { dialog.dismiss() }
        dialog.ok?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.andrax_launch_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val PACKAGE = "com.victorious.andrax"
        const val LAUNCHER_ACTIVITY = "com.victorious.andrax.MainActivity"
        const val RELEASES_URL = VsLinks.ANDRAX_RELEASES

        @JvmStatic
        fun explicitIntent(): Intent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(PACKAGE, LAUNCHER_ACTIVITY)

        /** Launcher intent if the app has one; otherwise the explicit component if it resolves; else null. */
        @JvmStatic
        fun <T> pickLaunchIntent(launcherIntent: T?, explicitIntent: T, resolves: (T) -> Boolean): T? =
            launcherIntent ?: explicitIntent.takeIf(resolves)
    }
}
