package com.termux.victor.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.termux.R

/**
 * Applies a fleet palette to hand-coloured View/XML chrome (the left/right drawers and the
 * VictorSuite settings screens). Never touches the terminal.
 *
 * Each view's built-in colour is remembered in a view tag, so passing `null` restores the
 * built-in theme, and a colour later reset by an adapter (e.g. the session list) is re-read
 * rather than overwritten blindly. Only colours listed in [FleetThemeRoleMap] are remapped.
 */
object FleetThemeApplier {

    /** Remembers the built-in colour and what we last painted over it. */
    private data class Remembered(val original: Int, val applied: Int)

    /** Walk [root] and apply [palette] (null = built-in). Main thread. */
    fun apply(root: View?, palette: FleetPalette?) {
        if (root == null) return
        applyBackground(root, palette)
        if (root is TextView) applyText(root, palette)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) apply(root.getChildAt(i), palette)
        }
    }

    private fun originalFor(view: View, tagKey: Int, current: Int): Int {
        val r = view.getTag(tagKey) as? Remembered
        // If the view still shows what we painted, its built-in colour is the remembered one;
        // otherwise something (an adapter, a click handler) set a new built-in colour.
        return if (r != null && r.applied == current) r.original else current
    }

    private fun applyBackground(view: View, palette: FleetPalette?) {
        val current: Int = when {
            view is CardView -> view.cardBackgroundColor.defaultColor
            view.background is ColorDrawable -> (view.background as ColorDrawable).color
            else -> return
        }
        val original = originalFor(view, R.id.fleet_theme_tag_background, current)
        val target = FleetThemeRoleMap.resolve(palette, FleetThemeRoleMap.backgroundRole(original), original)
        if (target != current) {
            if (view is CardView) view.setCardBackgroundColor(target) else view.setBackgroundColor(target)
        }
        view.setTag(R.id.fleet_theme_tag_background, Remembered(original, target))
    }

    private fun applyText(view: TextView, palette: FleetPalette?) {
        // Stateful colour lists (pressed/disabled variants) are left alone.
        if (view.textColors.isStateful) return
        val current = view.currentTextColor
        val original = originalFor(view, R.id.fleet_theme_tag_text, current)
        val target = FleetThemeRoleMap.resolve(palette, FleetThemeRoleMap.textRole(original), original)
        if (target != current) view.setTextColor(target)
        view.setTag(R.id.fleet_theme_tag_text, Remembered(original, target))
    }

    /**
     * Settings chrome for activities that use the platform action bar (stock Termux settings):
     * bar background = `surface`, title = `onBackground`. Applied only when both keys are present
     * (otherwise the built-in bar stays). Returns true if the bar was themed.
     */
    fun applyActionBar(activity: AppCompatActivity, palette: FleetPalette?): Boolean {
        val bar = activity.supportActionBar ?: return false
        val surface = palette?.colorFor(FleetRole.SURFACE)
        val onSurface = palette?.colorFor(FleetRole.ON_SURFACE)
        if (surface == null || onSurface == null) return false
        bar.setBackgroundDrawable(ColorDrawable(surface))
        val title = activity.title?.toString() ?: return true
        val span = SpannableString(title)
        span.setSpan(ForegroundColorSpan(onSurface), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        bar.title = span
        return true
    }

    /** Content root of an activity (the views inflated by setContentView). */
    fun contentRoot(activity: Activity): View? = activity.findViewById(android.R.id.content)
}
