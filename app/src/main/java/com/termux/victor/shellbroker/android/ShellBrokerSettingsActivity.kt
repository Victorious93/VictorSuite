package com.termux.victor.shellbroker.android

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.termux.victor.shellbroker.Scope
import com.termux.victor.shellbroker.TrustedCaller

/**
 * Owner controls for remote shell access: master switch (off), separate privileged switch (off), the trusted-app list with the
 * pinned certificate fingerprint, per-app scopes (terminal / one-shot / privileged are independent), live sessions with Disconnect,
 * and Revoke. Revoking or switching off ends running shells immediately.
 */
class ShellBrokerSettingsActivity : Activity() {
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Remote shell access"
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        setContentView(ScrollView(this).apply { addView(box) })
    }

    override fun onResume() { super.onResume(); render() }

    private fun text(s: String, size: Float = 14f, bold: Boolean = false) = TextView(this).apply { text = s; textSize = size; setPadding(0, 10, 0, 10); if (bold) setTypeface(typeface, Typeface.BOLD) }

    private fun render() {
        val broker = BrokerHolder.get(this); val settings = BrokerHolder.settings; val trust = BrokerHolder.trust
        box.removeAllViews()
        box.addView(text("Remote shell access", 20f, true))
        box.addView(text("Lets an app you trust — normally DroidCommand AI relaying your VictorLinux PC, or rustdesk — open a terminal or run one command on this phone. Every request still needs your tap on Allow. Off by default."))
        box.addView(Switch(this).apply { text = "Allow remote shell requests"; isChecked = settings.enabled()
            setOnCheckedChangeListener { _: CompoundButton, on: Boolean -> settings.setEnabled(on); broker.enforceTrust(); render() } })
        box.addView(Switch(this).apply { text = "Allow privileged (root) shells — separate grant"; isChecked = settings.rootAllowed(); isEnabled = settings.enabled()
            setOnCheckedChangeListener { _: CompoundButton, on: Boolean -> settings.setRootAllowed(on); broker.enforceTrust(); render() } })
        box.addView(text("Trusted apps", 16f, true))
        val rows = trust.all()
        if (rows.isEmpty()) box.addView(text("None. Nothing can reach the shell broker."))
        rows.forEach { r ->
            box.addView(text("${r.label}\n${r.packageName}\ncertificate sha256:\n${r.certSha256.chunked(32).joinToString("\n")}"))
            box.addView(scopeRow(r))
            box.addView(Button(this).apply { text = "Revoke ${r.label}"; setOnClickListener { trust.remove(r.packageName); render() } })
        }
        box.addView(Button(this).apply { text = "Trust an installed app…"; setOnClickListener { pickApp() } })
        box.addView(text("Live sessions", 16f, true))
        val live = broker.liveSessions()
        if (live.isEmpty()) box.addView(text("None."))
        live.forEach { (id, pkg, st) -> box.addView(text("$pkg — ${st.wire} — ${id.take(14)}")) }
        box.addView(Button(this).apply { text = "Disconnect everything now"; setOnClickListener { broker.revokeAll("disconnected by the owner"); render() } })
        val pend = ApprovalRegistry.pendingPrompts()
        if (pend.isNotEmpty()) box.addView(text("${pend.size} request(s) waiting for your approval — see the notification."))
    }

    private fun scopeRow(r: TrustedCaller): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        for ((scope, label) in listOf(Scope.TERMINAL to "Interactive terminal", Scope.ONESHOT to "One command at a time", Scope.PRIVILEGED to "Privileged (root) — in addition", Scope.TRANSPORT_PROVIDER to "Terminal transport (rustdesk: carries Remote shell)")) {
            addView(CheckBox(this@ShellBrokerSettingsActivity).apply {
                text = label; isChecked = scope in r.scopes
                setOnCheckedChangeListener { _, on ->
                    val cur = BrokerHolder.trust.find(r.packageName) ?: return@setOnCheckedChangeListener
                    BrokerHolder.trust.upsert(cur.copy(scopes = if (on) cur.scopes + scope else cur.scopes - scope))
                }
            })
        }
    }

    private fun pickApp() {
        val input = EditText(this).apply { hint = "package name, e.g. ai.droidcommand.app"; setText("ai.droidcommand.app") }
        AlertDialog.Builder(this).setTitle("Trust an installed app").setView(input)
            .setPositiveButton("Next") { _, _ -> confirm(input.text.toString().trim()) }.setNegativeButton("Cancel", null).show()
    }

    private fun confirm(pkg: String) {
        val ids = AndroidPackageIdentity(this)
        val cert = ids.certSha256(pkg)
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: PackageManager.NameNotFoundException) { null }
        if (cert == null || label == null) { AlertDialog.Builder(this).setMessage("'$pkg' is not installed or has no readable signing certificate.").setPositiveButton("OK", null).show(); return }
        AlertDialog.Builder(this).setTitle("Trust $label?")
            .setMessage("$pkg\n\nSigning certificate (SHA-256):\n${cert.chunked(32).joinToString("\n")}\n\nIt will be able to ask for a shell on this phone. Each request still needs your approval. Only this exact certificate is trusted; an app signed by anyone else with the same name is refused. Terminal and one-shot access are granted now; privileged access is not.")
            .setPositiveButton("Trust") { _, _ -> BrokerHolder.trust.upsert(TrustedCaller(pkg, cert, setOf(Scope.TERMINAL, Scope.ONESHOT), label)); render() }
            .setNegativeButton("Cancel", null).show()
    }
}
