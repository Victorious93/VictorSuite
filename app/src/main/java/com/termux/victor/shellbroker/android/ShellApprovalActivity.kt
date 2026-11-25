package com.termux.victor.shellbroker.android

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.termux.victor.shellbroker.Decision
import com.termux.victor.shellbroker.Mode
import com.termux.victor.shellbroker.Privilege

/**
 * The owner's decision screen. Shows exactly what the approval binding covers: who asked, which app is relaying, the target, the
 * mode, the privilege and (one-shot) the full command. Touches are dropped while another window overlays it (tapjacking).
 */
class ShellApprovalActivity : Activity() {
    private var sessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)          // no screenshots of a command the owner is approving
        if (Build.VERSION.SDK_INT >= 31) window.setHideOverlayWindows(true)
        sessionId = intent.getStringExtra(EXTRA_SESSION)
        val prompt = sessionId?.let { ApprovalRegistry.prompt(it) }
        if (prompt == null) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48); setBackgroundColor(0xFF101418.toInt()) }
        fun tv(text: String, size: Float = 15f, bold: Boolean = false, color: Int = 0xFFE6E6E6.toInt(), mono: Boolean = false) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color); setPadding(0, 8, 0, 8)
            if (bold) setTypeface(typeface, Typeface.BOLD); if (mono) typeface = Typeface.MONOSPACE; setTextIsSelectable(false)
        }
        val root_ = prompt.privilege == Privilege.ROOT
        root.addView(tv(if (root_) "Allow a ROOT shell on this phone?" else "Allow a shell on this phone?", 20f, true, if (root_) 0xFFFF6B6B.toInt() else 0xFFFFFFFF.toInt()))
        root.addView(tv("Requested by: ${prompt.peer}"))
        root.addView(tv("Relayed by app: ${prompt.callerLabel}\n(${prompt.callerPackage})"))
        root.addView(tv("Runs on: ${prompt.target}"))
        root.addView(tv("Kind: " + if (prompt.mode == Mode.ONESHOT) "ONE command (output and exit status are sent back)" else "interactive terminal (live keyboard control)"))
        root.addView(tv("Privilege: " + if (root_) "ROOT (granted separately in VictorSuite)" else "ordinary VictorSuite user"))
        prompt.command?.let { root.addView(tv("Command:", 13f)); root.addView(tv(it, 14f, mono = true, color = 0xFF9EF0A8.toInt())) }
        root.addView(tv("Nothing runs unless you tap Allow. Denying, or doing nothing, runs nothing.", 13f, color = 0xFFB0B7C3.toInt()))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, 24, 0, 0) }
        fun btn(label: String, d: Decision) = Button(this).apply {
            text = label; filterTouchesWhenObscured = true
            setOnClickListener { ApprovalRegistry.decide(prompt.sessionId, d); finish() }
        }
        row.addView(btn("Deny", Decision.DENIED)); row.addView(btn("Allow", Decision.APPROVED))
        root.addView(row)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onBackPressed() { sessionId?.let { ApprovalRegistry.decide(it, Decision.DENIED) }; super.onBackPressed() }

    companion object { const val EXTRA_SESSION = "session_id" }
}
