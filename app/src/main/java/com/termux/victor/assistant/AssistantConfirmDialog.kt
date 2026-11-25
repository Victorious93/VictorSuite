package com.termux.victor.assistant

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.termux.R

/**
 * Fleet-styled preview/confirm dialog for `??`. It only reports the user's decision; it never runs anything.
 * Destructive commands get the magenta "Run anyway" path with Cancel as the default; there is no
 * "don't ask again".
 */
object AssistantConfirmDialog {

    fun show(context: Context, proposed: Resolution.Proposed, onDecision: (Boolean) -> Unit): AlertDialog {
        val destructive = proposed.verdict.destructive
        val bg = ContextCompat.getColor(context, R.color.vs_neon_bg)
        val cyan = ContextCompat.getColor(context, R.color.vs_neon_cyan)
        val magenta = ContextCompat.getColor(context, R.color.vs_neon_magenta)
        val accent = if (destructive) magenta else cyan
        val pad = (16 * context.resources.displayMetrics.density).toInt()

        fun text(s: String, color: Int, mono: Boolean = false) = TextView(context).apply {
            text = s
            setTextColor(color)
            if (mono) typeface = Typeface.MONOSPACE
            setPadding(0, pad / 2, 0, pad / 2)
        }

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(bg)
                setStroke((2 * context.resources.displayMetrics.density).toInt(), accent)
            }
            addView(text(
                context.getString(if (destructive) R.string.vs_assistant_title_destructive else R.string.vs_assistant_title_run),
                accent,
            ).apply { textSize = 18f; gravity = Gravity.START })
            addView(text(proposed.command, cyan, mono = true).apply { setTextIsSelectable(true) })
            if (proposed.intent.explain.isNotEmpty()) addView(text(proposed.intent.explain, cyan))
            proposed.verdict.reasons.forEach { addView(text(it, magenta)) }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(box)
            .setCancelable(true)
            .setOnCancelListener { onDecision(false) }
            .setNegativeButton(R.string.vs_assistant_cancel) { _, _ -> onDecision(false) }
            .setPositiveButton(if (destructive) R.string.vs_assistant_run_anyway else R.string.vs_assistant_run) { _, _ -> onDecision(true) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(cyan)
                if (destructive) requestFocus() // Cancel is the default for destroy-class commands
            }
        }
        dialog.show()
        return dialog
    }
}
