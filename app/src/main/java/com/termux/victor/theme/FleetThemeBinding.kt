package com.termux.victor.theme

import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * Lifecycle glue: call [onStart]/[onStop] from an activity. While started, the views returned
 * by [roots] (and optionally the action bar) follow the fleet theme live.
 */
class FleetThemeBinding(
    private val activity: AppCompatActivity,
    private val themeActionBar: Boolean = false,
    private val roots: () -> List<View?> = { listOf(FleetThemeApplier.contentRoot(activity)) },
) : FleetThemeClient.Listener {

    private var actionBarThemed = false

    fun onStart() = FleetThemeClient.addListener(activity, this)

    fun onStop() = FleetThemeClient.removeListener(this)

    /** Re-apply the last known theme, e.g. after new views were bound into a drawer. */
    fun reapply() = onFleetThemeChanged(FleetThemeClient.current)

    override fun onFleetThemeChanged(snapshot: FleetThemeSnapshot?) {
        if (activity.isFinishing) return
        val palette = snapshot?.palette
        for (root in roots()) FleetThemeApplier.apply(root, palette)
        if (themeActionBar) {
            val themed = FleetThemeApplier.applyActionBar(activity, palette)
            // The stock action bar has no simple "restore default": recreate to drop our colours.
            if (actionBarThemed && !themed) activity.recreate()
            actionBarThemed = themed
        }
    }
}
