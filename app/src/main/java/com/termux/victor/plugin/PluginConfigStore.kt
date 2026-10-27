package com.termux.zerocore.plugin

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest storage for per-plugin settings (API keys, tokens, endpoints, etc.).
 * Backed by AndroidX Security's EncryptedSharedPreferences (AES256-GCM values, AES256-SIV
 * keys) instead of the plain SharedPreferences the rest of the app uses for non-sensitive
 * settings (see [com.termux.zerocore.utils.SaveData]).
 */
object PluginConfigStore {
    private const val PREFS_NAME = "victorsuite_plugin_config"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        return prefs ?: synchronized(this) {
            prefs ?: build(context.applicationContext).also { prefs = it }
        }
    }

    private fun build(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun scopedKey(pluginId: String, key: String) = "$pluginId::$key"

    fun get(context: Context, pluginId: String, key: String, default: String? = null): String? {
        return prefs(context).getString(scopedKey(pluginId, key), default)
    }

    fun set(context: Context, pluginId: String, key: String, value: String) {
        prefs(context).edit().putString(scopedKey(pluginId, key), value).apply()
    }

    fun remove(context: Context, pluginId: String, key: String) {
        prefs(context).edit().remove(scopedKey(pluginId, key)).apply()
    }

    /** Removes every stored value for a plugin, e.g. when it is unregistered. */
    fun clearAllForPlugin(context: Context, pluginId: String, keys: List<String>) {
        val editor = prefs(context).edit()
        keys.forEach { key -> editor.remove(scopedKey(pluginId, key)) }
        editor.apply()
    }
}
