package com.termux.victor.config.mainmenu.config

import com.termux.victor.config.VsLinks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.example.xh_lib.utils.UUtils
import com.termux.R
import com.termux.victor.config.mainmenu.MainMenuConfig

/**
 * Drawer entry that hands off to the separate RootForge OS app (docs/SUITE_PLAN.md section 7.6).
 *
 * Only launches RootForge's main screen: RootForge does not export an OPEN action or
 * destination extras yet, so there are no per-feature entries here.
 * If RootForge is not installed, offers its download page instead of failing.
 */
class RootForgeOpenClickConfig : BaseMenuClickConfig() {
    override fun getType(): Int {
        return MainMenuConfig.CODE_SUITE
    }

    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.link_ico)
    }

    override fun getString(context: Context?): String? {
        return context?.getString(R.string.rootforge_open_title) ?: "Open RootForge"
    }

    override fun onClick(view: View?, context: Context?) {
        if (context == null) return
        val launchIntent = PACKAGES
            .firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent == null) {
            showNotInstalled(context)
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launchIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.rootforge_launch_failed, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, R.string.rootforge_launch_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showNotInstalled(context: Context) {
        val dialog = switchDialogShow(
            context.getString(R.string.rootforge_missing_title),
            context.getString(R.string.rootforge_missing_msg),
            context
        )
        dialog.ok?.text = UUtils.getString(R.string.rootforge_open_releases)
        dialog.cancel?.setOnClickListener { dialog.dismiss() }
        dialog.ok?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.rootforge_launch_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        /** Release package first, then the `.debug` build used during development. */
        private val PACKAGES = listOf("ai.rootforge.os", "ai.rootforge.os.debug")
        private const val RELEASES_URL = VsLinks.ROOTFORGE_RELEASES
    }
}
