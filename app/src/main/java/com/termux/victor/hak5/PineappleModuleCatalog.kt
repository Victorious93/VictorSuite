package com.termux.victor.hak5

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Runtime module/script discovery for the Pineapple (plan Phase 2 §3).
 *
 * The Tetra firmware exposes a different set of modules per version, so we NEVER ship a
 * fixed list. We query the device at runtime and parse whatever shape it returns
 * (array of names, array of objects, or a map), classifying each action's severity with
 * [Hak5ActionClassifier]. Unknown/limited firmware yields an empty, limited catalog
 * rather than inventing modules.
 */

data class ModuleActionInfo(
    val action: String,
    val severity: Hak5ActionSeverity,
    val description: String? = null
) {
    fun toPendingAction(moduleName: String): Hak5PendingAction {
        return Hak5PendingAction(
            id = "module:$moduleName:$action",
            title = "$moduleName · $action",
            summary = description ?: "Run $action on $moduleName",
            severity = severity,
            capabilityKey = "module:$moduleName:$action"
        )
    }
}

data class PineappleModule(
    val name: String,
    val title: String? = null,
    val actions: List<ModuleActionInfo> = emptyList()
) {
    val displayName: String get() = title?.takeIf { it.isNotBlank() } ?: name
}

data class ModuleCatalog(
    val modules: List<PineappleModule>,
    val limited: Boolean,
    val firmwareLabel: String,
    val source: String? = null
) {
    val isEmpty: Boolean get() = modules.isEmpty()

    companion object {
        fun limited(firmwareLabel: String, source: String? = null): ModuleCatalog =
            ModuleCatalog(emptyList(), limited = true, firmwareLabel = firmwareLabel, source = source)
    }
}

/**
 * Lists modules at runtime. Prefers a module-list candidate already confirmed in the
 * [CapabilityMap]; otherwise attempts each candidate best-effort. Any failure to find a
 * parseable module list degrades to a limited catalog.
 */
class PineappleModuleLister(
    private val client: PineappleClient = PineappleClient()
) {
    fun list(baseUrl: String, apiToken: String?, capabilities: CapabilityMap): ModuleCatalog {
        if (!capabilities.apiShapeConfirmed) {
            return ModuleCatalog.limited(capabilities.firmwareLabel, source = "api_shape_unconfirmed")
        }

        // Order: confirmed candidates first, then the rest as a best-effort fallback.
        val confirmedFirst = PineappleCandidates.MODULE_LIST_CANDIDATES.sortedByDescending {
            capabilities.confirmedActions.contains(it.key())
        }

        for (candidate in confirmedFirst) {
            val resp = client.postApi(baseUrl, candidate.toProbeRequest(apiToken))
            if (!FirmwareProbe.responseSuggestsActionExists(resp)) continue
            val modules = parseModules(resp.body)
            if (modules.isNotEmpty()) {
                return ModuleCatalog(
                    modules = modules,
                    limited = capabilities.limitedMode,
                    firmwareLabel = capabilities.firmwareLabel,
                    source = candidate.key()
                )
            }
        }
        return ModuleCatalog.limited(capabilities.firmwareLabel, source = "no_module_list")
    }

    companion object {
        /** Keys that commonly wrap a module list in a response object. */
        private val LIST_KEYS = listOf(
            "modules", "moduleList", "module_list", "payload", "data",
            "items", "list", "result", "results", "installed"
        )
        private val NAME_KEYS = listOf("name", "module", "title", "id", "slug", "label")
        private val TITLE_KEYS = listOf("title", "label", "displayName", "display_name")
        private val ACTION_KEYS = listOf("actions", "endpoints", "methods", "routes", "commands")

        fun parseModules(body: String?): List<PineappleModule> {
            val out = linkedMapOf<String, PineappleModule>()
            val root: JsonElement = try {
                com.google.gson.JsonParser.parseString(body ?: return emptyList())
            } catch (_: Exception) {
                return emptyList()
            }

            val array: JsonArray? = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject -> findArray(root.asJsonObject)
                else -> null
            }

            if (array != null) {
                for (el in array) addFromElement(el, out)
            } else if (root.isJsonObject) {
                // Map shape: { "ModuleName": {...}, ... }
                val obj = root.asJsonObject
                for ((key, value) in obj.entrySet()) {
                    if (value.isJsonObject || value.isJsonArray) {
                        val module = moduleFromKeyed(key, value)
                        if (module != null) out[module.name] = module
                    }
                }
            }
            return out.values.toList()
        }

        private fun findArray(obj: JsonObject): JsonArray? {
            for (k in LIST_KEYS) {
                val el = obj.get(k) ?: continue
                if (el.isJsonArray) return el.asJsonArray
                if (el.isJsonObject) {
                    val nested = findArray(el.asJsonObject)
                    if (nested != null) return nested
                }
            }
            // Any array value at the top level, as a last resort.
            for ((_, v) in obj.entrySet()) if (v.isJsonArray) return v.asJsonArray
            return null
        }

        private fun addFromElement(el: JsonElement, out: MutableMap<String, PineappleModule>) {
            if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                val name = el.asString.trim()
                if (name.isNotEmpty() && !out.containsKey(name)) out[name] = PineappleModule(name = name)
                return
            }
            if (el.isJsonObject) {
                val obj = el.asJsonObject
                val name = firstString(obj, NAME_KEYS) ?: return
                val title = firstString(obj, TITLE_KEYS)
                val actions = parseActions(obj, name)
                out[name] = PineappleModule(name = name, title = title, actions = actions)
            }
        }

        private fun moduleFromKeyed(key: String, value: JsonElement): PineappleModule? {
            val name = key.trim()
            if (name.isEmpty()) return null
            if (value.isJsonObject) {
                val obj = value.asJsonObject
                val title = firstString(obj, TITLE_KEYS)
                return PineappleModule(name = name, title = title, actions = parseActions(obj, name))
            }
            if (value.isJsonArray) {
                return PineappleModule(name = name, actions = parseActionArray(value.asJsonArray, name))
            }
            return PineappleModule(name = name)
        }

        private fun parseActions(obj: JsonObject, moduleName: String): List<ModuleActionInfo> {
            for (k in ACTION_KEYS) {
                val el = obj.get(k) ?: continue
                if (el.isJsonArray) return parseActionArray(el.asJsonArray, moduleName)
            }
            return emptyList()
        }

        private fun parseActionArray(arr: JsonArray, moduleName: String): List<ModuleActionInfo> {
            val actions = linkedMapOf<String, ModuleActionInfo>()
            for (el in arr) {
                val actionName = when {
                    el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString.trim()
                    el.isJsonObject -> firstString(el.asJsonObject, listOf("action", "name", "method", "endpoint", "id"))
                    else -> null
                } ?: continue
                if (actionName.isEmpty()) continue
                val desc = if (el.isJsonObject) firstString(el.asJsonObject, listOf("description", "desc", "summary")) else null
                actions[actionName] = ModuleActionInfo(
                    action = actionName,
                    severity = Hak5ActionClassifier.classify(actionName, moduleName),
                    description = desc
                )
            }
            return actions.values.toList()
        }

        private fun firstString(obj: JsonObject, keys: List<String>): String? {
            for (k in keys) {
                val el = obj.get(k) ?: continue
                if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                    val s = el.asString.trim()
                    if (s.isNotEmpty()) return s
                }
            }
            return null
        }
    }
}
