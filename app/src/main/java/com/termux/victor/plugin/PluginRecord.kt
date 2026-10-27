package com.termux.zerocore.plugin

/**
 * Persisted bookkeeping for a registered plugin: which manifest it was registered from,
 * its current enable state, and the audit timestamps that back the authorized-use gate.
 * Holds no secrets — plugin-supplied configuration values (API keys, credentials, etc.)
 * live in [PluginConfigStore] instead, encrypted at rest.
 */
data class PluginRecord(
    val manifestEntry: PluginManifestEntry,
    val state: PluginState = PluginState.DISABLED,
    val registeredAt: Long,
    val acknowledgedAuthorizedUseAt: Long? = null,
    val lastEnabledAt: Long? = null,
    val lastLaunchedAt: Long? = null
) {
    val id: String get() = manifestEntry.id
}
