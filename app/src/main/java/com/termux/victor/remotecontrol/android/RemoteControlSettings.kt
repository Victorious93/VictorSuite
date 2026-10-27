package com.termux.zerocore.remotecontrol.android

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.termux.zerocore.remotecontrol.DevicePairing
import com.termux.zerocore.remotecontrol.RemoteControlConfig
import java.security.SecureRandom

/**
 * Persisted remote-control settings (thin Android layer).
 * External control is OFF by default. Pairings are the caller allow-list.
 */
object RemoteControlSettings {
    private const val PREFS = "victorsuite_remote_control"
    private const val KEY_EXTERNAL_CONTROL = "external_control_enabled"
    private const val KEY_PAIRINGS = "pairings_json"
    private const val KEY_NODE_ID = "node_id"
    const val NODE_ID_PREFIX = "victorsuite-"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isExternalControlEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_EXTERNAL_CONTROL, false)

    fun setExternalControlEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXTERNAL_CONTROL, enabled).apply()
    }

    /** Stable node id, `victorsuite-<32 hex>`, generated once. */
    @Synchronized
    fun nodeId(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_NODE_ID, null)?.let { return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val id = NODE_ID_PREFIX + bytes.joinToString("") { "%02x".format(it) }
        p.edit().putString(KEY_NODE_ID, id).apply()
        return id
    }

    fun pairings(context: Context): List<DevicePairing> {
        val raw = prefs(context).getString(KEY_PAIRINGS, null) ?: return emptyList()
        return try {
            JsonParser.parseString(raw).asJsonArray.mapNotNull { el ->
                val o = el.asJsonObject
                val src = o.get("sourceNodeRef")?.asString.orEmpty()
                val pkg = o.get("packageName")?.asString.orEmpty()
                if (src.isBlank() || pkg.isBlank()) null
                else DevicePairing(src, pkg, o.get("certSha256")?.takeIf { !it.isJsonNull }?.asString)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setPairings(context: Context, pairings: List<DevicePairing>) {
        val arr = JsonArray()
        pairings.forEach { p ->
            arr.add(JsonObject().apply {
                addProperty("sourceNodeRef", p.sourceNodeRef)
                addProperty("packageName", p.packageName)
                p.certSha256?.let { addProperty("certSha256", it) }
            })
        }
        prefs(context).edit().putString(KEY_PAIRINGS, arr.toString()).apply()
    }

    fun load(context: Context): RemoteControlConfig =
        RemoteControlConfig(isExternalControlEnabled(context), pairings(context))

    /** Saves an imported config. External control is always written as OFF. */
    fun saveImported(context: Context, config: RemoteControlConfig) {
        setPairings(context, config.pairings)
        setExternalControlEnabled(context, false)
    }

    /**
     * Pairings editor format: one `packageName=sourceNodeRef,certSha256` per line (the cert is required; rows without it never authenticate).
     * Blank lines and `#` comments are ignored; malformed lines are skipped.
     */
    fun parsePairingsText(text: String): List<DevicePairing> = text.lineSequence().mapNotNull { line ->
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
        val eq = t.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        val pkg = t.substring(0, eq).trim()
        val rest = t.substring(eq + 1).split(',').map { it.trim() }
        val src = rest.getOrNull(0).orEmpty()
        if (pkg.isEmpty() || src.isEmpty()) return@mapNotNull null
        DevicePairing(src, pkg, rest.getOrNull(1)?.takeIf { it.isNotEmpty() })
    }.toList()

    fun formatPairingsText(pairings: List<DevicePairing>): String = pairings.joinToString("\n") { p ->
        "${p.packageName}=${p.sourceNodeRef}" + (p.certSha256?.let { ",$it" } ?: "")
    }
}
