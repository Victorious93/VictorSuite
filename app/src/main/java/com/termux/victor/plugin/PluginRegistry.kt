package com.termux.victor.plugin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * In-process registry of known plugins and their enable state, persisted to a plain JSON
 * file under the app's private storage (no secrets live here — see [PluginConfigStore]
 * for those).
 *
 * The enable path is deliberately a two-step gate:
 *   register() -> always DISABLED, never runnable
 *   acknowledgeAuthorizedUse() -> user records that they're authorized to use this plugin
 *   enable() -> only succeeds once acknowledgeAuthorizedUse() has been recorded
 *   disable()/unregister() -> always available
 *
 * [PluginProcessLauncher] separately refuses to resolve a launch command for anything
 * not in the ENABLED state, so both the registry and the launcher enforce the same
 * invariant independently.
 */
object PluginRegistry {
    private const val REGISTRY_FILE = "plugin_registry.json"
    private val gson = Gson()
    private val records = LinkedHashMap<String, PluginRecord>()

    @Volatile
    private var loaded = false

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        val file = registryFile(context)
        if (file.exists()) {
            try {
                val type = object : TypeToken<List<PluginRecord>>() {}.type
                val list: List<PluginRecord>? = gson.fromJson(file.readText(), type)
                list?.forEach { record -> records[record.id] = record }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        loaded = true
    }

    private fun registryFile(context: Context): File {
        return File(context.applicationContext.filesDir, REGISTRY_FILE)
    }

    @Synchronized
    private fun persist(context: Context) {
        try {
            registryFile(context).writeText(gson.toJson(records.values.toList()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun list(context: Context): List<PluginRecord> {
        ensureLoaded(context)
        return records.values.toList()
    }

    @Synchronized
    fun get(context: Context, id: String): PluginRecord? {
        ensureLoaded(context)
        return records[id]
    }

    /**
     * Registers or refreshes metadata for a plugin from a manifest entry. Always leaves
     * the plugin DISABLED — re-registering an already-enabled plugin does not keep it
     * enabled implicitly; call [enable] again if that's actually intended, so an updated
     * manifest can't silently re-arm something the user disabled.
     */
    @Synchronized
    fun register(context: Context, entry: PluginManifestEntry): PluginRecord {
        ensureLoaded(context)
        val existing = records[entry.id]
        val record = PluginRecord(
            manifestEntry = entry,
            state = PluginState.DISABLED,
            registeredAt = existing?.registeredAt ?: System.currentTimeMillis(),
            acknowledgedAuthorizedUseAt = existing?.acknowledgedAuthorizedUseAt,
            lastEnabledAt = existing?.lastEnabledAt,
            lastLaunchedAt = existing?.lastLaunchedAt
        )
        records[entry.id] = record
        persist(context)
        PluginLogger.log(context, entry.id, "REGISTERED", "version=${entry.version}")
        return record
    }

    /**
     * Records the user's one-time acknowledgment that they are authorized to use this
     * plugin in this environment. Required before [enable] will succeed.
     */
    @Synchronized
    fun acknowledgeAuthorizedUse(context: Context, id: String): PluginRecord? {
        ensureLoaded(context)
        val existing = records[id] ?: return null
        val updated = existing.copy(acknowledgedAuthorizedUseAt = System.currentTimeMillis())
        records[id] = updated
        persist(context)
        PluginLogger.log(context, id, "ACKNOWLEDGED_AUTHORIZED_USE")
        return updated
    }

    /** Enables a plugin. Fails unless authorized use has already been acknowledged. */
    @Synchronized
    fun enable(context: Context, id: String): Result<PluginRecord> {
        ensureLoaded(context)
        val existing = records[id]
            ?: return Result.failure(IllegalArgumentException("Unknown plugin: $id"))
        if (existing.acknowledgedAuthorizedUseAt == null) {
            return Result.failure(
                IllegalStateException(
                    "Plugin '$id' must be acknowledged as authorized (acknowledgeAuthorizedUse) before it can be enabled"
                )
            )
        }
        val updated = existing.copy(state = PluginState.ENABLED, lastEnabledAt = System.currentTimeMillis())
        records[id] = updated
        persist(context)
        PluginLogger.log(context, id, "ENABLED")
        return Result.success(updated)
    }

    @Synchronized
    fun disable(context: Context, id: String): Result<PluginRecord> {
        ensureLoaded(context)
        val existing = records[id]
            ?: return Result.failure(IllegalArgumentException("Unknown plugin: $id"))
        val updated = existing.copy(state = PluginState.DISABLED)
        records[id] = updated
        persist(context)
        PluginLogger.log(context, id, "DISABLED")
        return Result.success(updated)
    }

    /** Removes a plugin's registry record and its stored config values. */
    @Synchronized
    fun unregister(context: Context, id: String) {
        ensureLoaded(context)
        val existing = records.remove(id) ?: return
        persist(context)
        val keys = existing.manifestEntry.configSchema.map { field -> field.key }
        PluginConfigStore.clearAllForPlugin(context, id, keys)
        PluginLogger.log(context, id, "UNREGISTERED")
    }

    @Synchronized
    fun recordLaunch(context: Context, id: String) {
        ensureLoaded(context)
        val existing = records[id] ?: return
        records[id] = existing.copy(lastLaunchedAt = System.currentTimeMillis())
        persist(context)
    }
}
