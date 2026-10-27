package com.termux.victor.plugin

/**
 * Top-level manifest schema fetched from `remote/plugins/main.json` (or a user-configured
 * alternate source). Mirrors the shape of the existing module-manifest system
 * (see [com.termux.victor.bean.ZDYDataBean]) so it fits the same fetch/parse code path.
 */
data class PluginManifest(
    val code: Int,
    val `data`: List<PluginManifestEntry>,
    val home: String,
    val ip: String,
    val versionName: String,
    val serviceName: String,
    val msg: String
)

/**
 * Describes one plugin. This is metadata only — it never contains executable code or a
 * download for one. `entryPoint` names a file the user is expected to have installed
 * themselves (e.g. via the package manager, or by hand) under the plugin's install
 * directory; the framework will not run anything that isn't already present on disk.
 */
data class PluginManifestEntry(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    // Free-text grouping for display only (e.g. "network", "dev-tools"). Intentionally
    // not a fixed enum of tool categories — this framework is tool-agnostic by design.
    val category: String,
    // Path to the executable/script, relative to the plugin's install directory. The
    // user is responsible for putting a real file there; nothing here fetches it.
    val entryPoint: String,
    val args: List<String> = emptyList(),
    val configSchema: List<PluginConfigField> = emptyList(),
    val docsUrl: String? = null
)

enum class PluginConfigFieldType {
    TEXT, SECRET, BOOL, NUMBER
}

/** One configurable setting a plugin exposes, rendered by the (future) settings UI. */
data class PluginConfigField(
    val key: String,
    val label: String,
    val type: PluginConfigFieldType,
    val default: String? = null
)
