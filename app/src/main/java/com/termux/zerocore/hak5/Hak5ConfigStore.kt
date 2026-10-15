package com.termux.zerocore.hak5

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest storage for Hak5 companion settings (management host, API token,
 * cached firmware string). Same EncryptedSharedPreferences pattern as PluginConfigStore.
 */
object Hak5ConfigStore {
    private const val PREFS_NAME = "victorsuite_hak5_config"

    const val DEFAULT_HOST = "172.16.42.1"
    const val DEFAULT_PORT = 1471

    private const val KEY_HOST = "management_host"
    private const val KEY_PORT = "management_port"
    private const val KEY_API_TOKEN = "api_token"
    private const val KEY_LAST_FIRMWARE = "last_firmware"
    private const val KEY_LAST_CAPABILITIES_JSON = "last_capabilities_json"
    private const val KEY_LAST_CHECKED_AT = "last_checked_at"
    private const val KEY_LAST_REACHABLE = "last_reachable"

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

    fun getHost(context: Context): String {
        return prefs(context).getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
    }

    fun setHost(context: Context, host: String) {
        prefs(context).edit().putString(KEY_HOST, host.trim()).apply()
    }

    fun getPort(context: Context): Int {
        return prefs(context).getInt(KEY_PORT, DEFAULT_PORT)
    }

    fun setPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PORT, port).apply()
    }

    fun getApiToken(context: Context): String? {
        return prefs(context).getString(KEY_API_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    fun setApiToken(context: Context, token: String?) {
        val editor = prefs(context).edit()
        if (token.isNullOrBlank()) editor.remove(KEY_API_TOKEN)
        else editor.putString(KEY_API_TOKEN, token.trim())
        editor.apply()
    }

    fun getLastFirmware(context: Context): String? {
        return prefs(context).getString(KEY_LAST_FIRMWARE, null)
    }

    fun setLastFirmware(context: Context, firmware: String?) {
        val editor = prefs(context).edit()
        if (firmware.isNullOrBlank()) editor.remove(KEY_LAST_FIRMWARE)
        else editor.putString(KEY_LAST_FIRMWARE, firmware)
        editor.apply()
    }

    fun getLastCapabilitiesJson(context: Context): String? {
        return prefs(context).getString(KEY_LAST_CAPABILITIES_JSON, null)
    }

    fun setLastCapabilitiesJson(context: Context, json: String?) {
        val editor = prefs(context).edit()
        if (json.isNullOrBlank()) editor.remove(KEY_LAST_CAPABILITIES_JSON)
        else editor.putString(KEY_LAST_CAPABILITIES_JSON, json)
        editor.apply()
    }

    fun getLastCheckedAt(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_CHECKED_AT, 0L)
    }

    fun setLastCheckedAt(context: Context, epochMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_CHECKED_AT, epochMs).apply()
    }

    fun getLastReachable(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_LAST_REACHABLE, false)
    }

    fun setLastReachable(context: Context, reachable: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAST_REACHABLE, reachable).apply()
    }

    /** Clears stored API token (kill-switch helper). Host/port retained. */
    fun clearSecrets(context: Context) {
        prefs(context).edit().remove(KEY_API_TOKEN).apply()
    }

    fun baseUrl(context: Context): String {
        return buildBaseUrl(getHost(context), getPort(context))
    }

    fun buildBaseUrl(host: String, port: Int): String {
        val h = host.trim().trimEnd('/')
        return "http://$h:$port"
    }
}
