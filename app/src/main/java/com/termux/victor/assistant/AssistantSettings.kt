package com.termux.victor.assistant

import android.content.Context

/** Settings for the `??` terminal assistant. On by default. The optional LLM stays off. */
object AssistantSettings {
    private const val PREFS = "victorsuite_assistant"
    private const val KEY_ENABLED = "enabled"

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        AssistantBootstrap.setEnabledFlag(enabled)
        if (enabled) {
            AssistantInstaller.install(context)
        }
    }
}
