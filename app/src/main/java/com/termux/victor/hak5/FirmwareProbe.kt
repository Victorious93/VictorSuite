package com.termux.victor.hak5

/**
 * Runtime firmware / capability detection for Tetra 6th-gen API (plan §3.5).
 *
 * 1. Confirm API shape with documented listNotifications.
 * 2. Try version-candidate read-only actions; first useful hint wins.
 * 3. Probe other read-only candidates; never fire state-changing actions.
 * 4. Unknown firmware → limited / read-only mode.
 *
 * Pure logic over [PineappleClient] responses — unit-testable with fixtures.
 */
class FirmwareProbe(
    private val client: PineappleClient = PineappleClient()
) {
    fun probe(baseUrl: String, apiToken: String?): CapabilityMap {
        val confirmed = linkedSetOf<String>()
        var apiShapeConfirmed = false
        val versionHints = linkedMapOf<String, String>()

        // Step 1: documented harmless call confirms 6th-gen /api/ + token model.
        val shapeReq = PineappleCandidates.LIST_NOTIFICATIONS.toProbeRequest(apiToken)
        val shapeResp = client.postApi(baseUrl, shapeReq)
        if (shapeResp.isJson) {
            apiShapeConfirmed = true
            confirmed.add(PineappleCandidates.LIST_NOTIFICATIONS.key())
        } else if (shapeResp.httpStatus != null && shapeResp.ok && shapeResp.body != null) {
            // Some firmwares may return non-JSON; still treat successful POST as weak signal.
            if (shapeResp.body!!.trim().startsWith("{") || shapeResp.body!!.trim().startsWith("[")) {
                apiShapeConfirmed = true
                confirmed.add(PineappleCandidates.LIST_NOTIFICATIONS.key())
            }
        }

        if (!apiShapeConfirmed) {
            return CapabilityMap.unknown(apiShapeConfirmed = false)
        }

        // Step 2: version candidates (read-only only).
        var firmwareLabel: String? = null
        for (candidate in PineappleCandidates.VERSION_CANDIDATES) {
            val resp = client.postApi(baseUrl, candidate.toProbeRequest(apiToken))
            if (!responseSuggestsActionExists(resp)) continue
            confirmed.add(candidate.key())
            val hint = PineappleJson.extractVersionHint(resp.body)
            if (!hint.isNullOrBlank()) {
                versionHints[candidate.key()] = hint
                if (firmwareLabel == null) firmwareLabel = hint
            }
        }

        // Step 3: module-list candidates (read-only).
        for (candidate in PineappleCandidates.MODULE_LIST_CANDIDATES) {
            val resp = client.postApi(baseUrl, candidate.toProbeRequest(apiToken))
            if (responseSuggestsActionExists(resp)) {
                confirmed.add(candidate.key())
            }
        }

        val label = firmwareLabel ?: "unknown"
        val limited = firmwareLabel == null
        return CapabilityMap(
            firmwareLabel = label,
            apiShapeConfirmed = true,
            confirmedActions = confirmed,
            limitedMode = limited,
            rawVersionHints = versionHints
        )
    }

    /**
     * Connect = discover reachability, then probe capabilities if reachable.
     * Never auto-attacks; probe uses read-only candidates only.
     */
    fun connect(baseUrl: String, apiToken: String?): ConnectResult {
        val discovery = client.discover(baseUrl, apiToken)
        if (!discovery.reachable) {
            return ConnectResult(
                discovery = discovery,
                capabilities = CapabilityMap.unknown(false),
                usedToken = !apiToken.isNullOrBlank()
            )
        }
        val caps = probe(baseUrl, apiToken)
        return ConnectResult(
            discovery = discovery,
            capabilities = caps,
            usedToken = !apiToken.isNullOrBlank()
        )
    }

    companion object {
        /**
         * Heuristic: a JSON body that is not an obvious "unknown action/module" error
         * suggests the action exists. Lenient — prefer false negatives over claiming
         * an action is available when it is not.
         */
        fun responseSuggestsActionExists(resp: PineappleClient.ApiResponse): Boolean {
            if (resp.httpStatus == null) return false
            val body = resp.body?.lowercase().orEmpty()
            if (body.isBlank()) return resp.ok
            // Common rejection phrases (best-effort; unknown firmwares vary).
            val rejection = listOf(
                "unknown action",
                "invalid action",
                "action not found",
                "unknown module",
                "module not found",
                "not found",
                "invalid module",
                "unauthorized",
                "invalid token",
                "api token"
            )
            // Invalid token still means the route exists; do not count as confirmed action.
            if ("invalid token" in body || "unauthorized" in body) return false
            if (!resp.isJson && !resp.ok) return false
            if (rejection.any { it in body } && "notification" !in body) {
                // listNotifications success bodies may contain benign words; keep narrow.
                if ("unknown" in body || "invalid" in body || "not found" in body) return false
            }
            return resp.isJson || resp.ok
        }
    }
}
