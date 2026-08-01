package com.termux.zerocore.config.mainmenu.config

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.termux.R
import com.termux.zerocore.utils.SingletonCommunicationUtils

/**
 * Shortcuts for standard, publicly available APK-analysis / network-diagnostic
 * tools (apktool, jadx, nmap) already published in the Termux package repos.
 * Intended for inspecting your own APKs and personal network diagnostics.
 */
class ApkAnalysisClickConfig : BaseMenuClickConfig() {
    private val tools = arrayOf(
        "apktool" to "Decode/rebuild APK resources and smali (apktool)",
        "jadx" to "Decompile APKs to readable Java source (jadx)",
        "nmap" to "Network discovery / diagnostic scanner (nmap)"
    )
    private val checked = BooleanArray(tools.size)

    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.apk_img)
    }

    override fun getString(context: Context?): String? {
        return "APK Analysis Tools"
    }

    override fun onClick(view: View?, context: Context?) {
        val ctx = context ?: return
        val labels = tools.map { "${it.first} — ${it.second}" }.toTypedArray()

        AlertDialog.Builder(ctx)
            .setTitle("Install analysis tools")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Install selected") { _, _ ->
                val selected = tools.indices.filter { checked[it] }.map { tools[it].first }
                if (selected.isNotEmpty()) {
                    val command = "pkg install -y ${selected.joinToString(" ")}\n"
                    SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener()
                        .sendTextToTerminal(command)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
