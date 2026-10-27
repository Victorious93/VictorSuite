package com.termux.victor.hak5

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Data models for WiFi Pineapple Tetra 6th-gen API (POST /api/).
 * Module/action names are candidates only — validated at runtime (see [FirmwareProbe]).
 */
data class PineappleApiRequest(
    val system: String? = null,
    val module: String? = null,
    val action: String,
    val apiToken: String? = null,
    val extra: Map<String, Any?> = emptyMap()
) {
    init {
        require(system != null || module != null) {
            "API request must set either system or module"
        }
        require(system == null || module == null) {
            "API request must not set both system and module"
        }
    }

    fun toJsonObject(): JsonObject {
        val obj = JsonObject()
        when {
            system != null -> obj.addProperty("system", system)
            module != null -> obj.addProperty("module", module)
        }
        obj.addProperty("action", action)
        if (!apiToken.isNullOrBlank()) {
            obj.addProperty("apiToken", apiToken)
        }
        for ((k, v) in extra) {
            when (v) {
                null -> { /* skip */ }
                is String -> obj.addProperty(k, v)
                is Number -> obj.addProperty(k, v)
                is Boolean -> obj.addProperty(k, v)
                else -> obj.addProperty(k, v.toString())
            }
        }
        return obj
    }

    fun toJson(): String = toJsonObject().toString()
}

data class CandidateAction(
    val kind: Kind,
    val name: String,
    val action: String,
    val readOnly: Boolean,
    val description: String
) {
    enum class Kind { SYSTEM, MODULE }

    fun toProbeRequest(apiToken: String?): PineappleApiRequest {
        return if (kind == Kind.SYSTEM) {
            PineappleApiRequest(system = name, action = action, apiToken = apiToken)
        } else {
            PineappleApiRequest(module = name, action = action, apiToken = apiToken)
        }
    }

    fun key(): String = "${kind.name.lowercase()}:$name:$action"
}

/**
 * Shipped candidate (system|module, action) pairs. Only read-only candidates are
 * ever fired as probes. State-changing ones are listed for Phase 2 UI but never
 * auto-probed. Presence of non-read-only actions is inferred from module listings
 * once those are confirmed.
 */
object PineappleCandidates {
    /** Documented Hak5 example — safe read-only probe of API shape. */
    val LIST_NOTIFICATIONS = CandidateAction(
        kind = CandidateAction.Kind.SYSTEM,
        name = "notifications",
        action = "listNotifications",
        readOnly = true,
        description = "List notifications (documented 6th-gen example)"
    )

    /**
     * Candidate version/status readers. Which one works depends on firmware —
     * tried in order; first success wins. Marked "to be confirmed against real device".
     */
    val VERSION_CANDIDATES = listOf(
        CandidateAction(
            CandidateAction.Kind.SYSTEM, "management", "getDeviceStatus",
            readOnly = true, description = "Candidate device status (unconfirmed)"
        ),
        CandidateAction(
            CandidateAction.Kind.SYSTEM, "management", "getSystemInfo",
            readOnly = true, description = "Candidate system info (unconfirmed)"
        ),
        CandidateAction(
            CandidateAction.Kind.SYSTEM, "setup", "getStatus",
            readOnly = true, description = "Candidate setup status (unconfirmed)"
        ),
        CandidateAction(
            CandidateAction.Kind.MODULE, "Dashboard", "getOverview",
            readOnly = true, description = "Candidate dashboard overview (unconfirmed)"
        )
    )

    val MODULE_LIST_CANDIDATES = listOf(
        CandidateAction(
            CandidateAction.Kind.SYSTEM, "modules", "listModules",
            readOnly = true, description = "Candidate list modules (unconfirmed)"
        ),
        CandidateAction(
            CandidateAction.Kind.SYSTEM, "modules", "getModuleList",
            readOnly = true, description = "Candidate getModuleList (unconfirmed)"
        )
    )

    /** All read-only candidates the companion may probe on connect. */
    fun allReadOnlyProbes(): List<CandidateAction> {
        return listOf(LIST_NOTIFICATIONS) + VERSION_CANDIDATES + MODULE_LIST_CANDIDATES
    }
}

data class CapabilityMap(
    val firmwareLabel: String,
    val apiShapeConfirmed: Boolean,
    val confirmedActions: Set<String>,
    val limitedMode: Boolean,
    val rawVersionHints: Map<String, String> = emptyMap()
) {
    fun isConfirmed(candidate: CandidateAction): Boolean = confirmedActions.contains(candidate.key())

    fun toSummaryJson(): String {
        val obj = JsonObject()
        obj.addProperty("firmwareLabel", firmwareLabel)
        obj.addProperty("apiShapeConfirmed", apiShapeConfirmed)
        obj.addProperty("limitedMode", limitedMode)
        val actions = com.google.gson.JsonArray()
        confirmedActions.sorted().forEach { actions.add(it) }
        obj.add("confirmedActions", actions)
        return obj.toString()
    }

    companion object {
        fun unknown(apiShapeConfirmed: Boolean = false): CapabilityMap {
            return CapabilityMap(
                firmwareLabel = if (apiShapeConfirmed) "unknown" else "unreachable_or_unsupported",
                apiShapeConfirmed = apiShapeConfirmed,
                confirmedActions = emptySet(),
                limitedMode = true
            )
        }
    }
}

data class DiscoveryResult(
    val reachable: Boolean,
    val baseUrl: String,
    val httpStatus: Int? = null,
    val identityHint: String? = null,
    val errorMessage: String? = null,
    val elapsedMs: Long = 0L
)

data class ConnectResult(
    val discovery: DiscoveryResult,
    val capabilities: CapabilityMap,
    val usedToken: Boolean
)

/** Lenient JSON helpers — ignore unknown fields, never crash on odd shapes. */
object PineappleJson {
    fun parseObject(body: String?): JsonObject? {
        if (body.isNullOrBlank()) return null
        return try {
            val el = JsonParser.parseString(body)
            if (el.isJsonObject) el.asJsonObject else null
        } catch (_: Exception) {
            null
        }
    }

    fun findString(obj: JsonObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (key in keys) {
            val el = findElement(obj, key) ?: continue
            if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                val s = el.asString
                if (s.isNotBlank()) return s
            } else if (el.isJsonPrimitive) {
                return el.toString().trim('"')
            }
        }
        return null
    }

    private fun findElement(obj: JsonObject, key: String): JsonElement? {
        if (obj.has(key)) return obj.get(key)
        // shallow nested search one level
        for ((_, v) in obj.entrySet()) {
            if (v.isJsonObject && v.asJsonObject.has(key)) {
                return v.asJsonObject.get(key)
            }
        }
        return null
    }

    /**
     * Heuristic: extract a firmware / version string from a JSON response body.
     * Looks for common key names without asserting a specific schema.
     */
    fun extractVersionHint(body: String?): String? {
        val obj = parseObject(body) ?: return null
        return findString(
            obj,
            "firmware", "firmwareVersion", "firmware_version", "version",
            "systemVersion", "pineappleVersion", "release", "build"
        )
    }

    /** True if the body looks like a JSON API reply (not an HTML login page). */
    fun looksLikeApiJson(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val trimmed = body.trim()
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return false
        return parseObject(body) != null || trimmed.startsWith("[")
    }
}
