package com.termux.victor.victorfiles

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.termux.R

/**
 * Hand-off to VictorFiles' right-edge file manager sheet, which replaces the built-in file window of
 * the right drawer. VictorFiles is a separate APK, so the sheet is launched (never embedded); it is
 * a translucent activity that slides over this app. Contract: VictorFiles docs/VICTORSUITE_PANEL.md.
 *
 * Every entry point is a no-op or returns false when no installed VictorFiles provides the sheet, so
 * callers fall back to the built-in browser.
 */
object VictorFilesPanel {
    const val ACTION_PANEL = "ai.victorious.filemanager.action.PANEL"
    const val EXTRA_OPEN_PATH = "ai.victorious.filemanager.extra.OPEN_PATH"
    const val PLACEHOLDER_TAG = "victorfiles_placeholder"

    /** Release package first, then the debug build's ".debug" suffix. */
    private val PACKAGES = arrayOf("ai.victorious.filemanager", "ai.victorious.filemanager.debug")
    private const val PREFS = "victorfiles_panel"
    private const val KEY_ENABLED = "use_panel"

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Explicit intent for the sheet, or null when no installed VictorFiles provides it. */
    @JvmStatic
    fun panelIntent(context: Context): Intent? {
        val packageManager = context.packageManager
        for (pkg in PACKAGES) {
            val intent = Intent(ACTION_PANEL).setPackage(pkg)
            if (packageManager.resolveActivity(intent, 0) != null) return intent
        }
        return null
    }

    /** True when the user has not switched the sheet off and an installed VictorFiles provides it. */
    @JvmStatic
    fun isActive(context: Context): Boolean = isEnabled(context) && panelIntent(context) != null

    /**
     * Opens the sheet over [activity]. No FLAG_ACTIVITY_NEW_TASK: it should stack in this task so
     * back returns straight to the drawer. Returns false if it could not be started.
     */
    @JvmStatic
    @JvmOverloads
    fun launch(activity: Activity, path: String? = null): Boolean {
        val intent = panelIntent(activity) ?: return false
        if (path != null) intent.putExtra(EXTRA_OPEN_PATH, path)
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Shown in the drawer's file slot in place of the built-in browser: one tap reopens the sheet. */
    @JvmStatic
    fun buildPlaceholder(context: Context, onOpen: View.OnClickListener): View {
        val button = Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            setText(R.string.victorfiles_open)
            setTextColor(Color.WHITE)
            setOnClickListener(onOpen)
        }
        return FrameLayout(context).apply {
            tag = PLACEHOLDER_TAG
            addView(button, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
    }
}
