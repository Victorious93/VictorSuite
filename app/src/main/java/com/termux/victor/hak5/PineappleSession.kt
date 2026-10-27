package com.termux.zerocore.hak5

/**
 * Authenticated Pineapple API session (plan Phase 2 §1 and §2).
 *
 * The 6th-gen Tetra API is token-authenticated per request: every POST /api/ carries
 * the apiToken in the JSON body (see [PineappleApiRequest]). There is no cookie session
 * to hold, so a "session" here means: a token we have validated against the device plus
 * the capability map and status snapshot we read while validating it. We default to the
 * apiToken model (SSH is intentionally out of scope for Phase 2 — see the phase doc).
 *
 * The token is NEVER logged; [Hak5AuditLog]/[Hak5CommandLog] redact it, and this class
 * never writes it anywhere. Pure logic over [PineappleClient] — unit-testable with
 * MockWebServer.
 */
class PineappleSession(
    private val client: PineappleClient = PineappleClient(),
    private val probe: FirmwareProbe = FirmwareProbe(client)
) {

    /** Device/firmware/connection snapshot surfaced in the status view. */
    data class DeviceStatus(
        val reachable: Boolean,
        val apiShapeConfirmed: Boolean,
        val limitedMode: Boolean,
        val firmware: String? = null,
        val model: String? = null,
        val version: String? = null,
        val uptimeSeconds: Long? = null,
        val identityHint: String? = null,
        val rawHints: Map<String, String> = emptyMap()
    )

    data class LoginResult(
        val authenticated: Boolean,
        val baseUrl: String,
        val capabilities: CapabilityMap,
        val status: DeviceStatus,
        /** True when the device replied but rejected the token (bad/expired apiToken). */
        val tokenRejected: Boolean = false,
        val httpStatus: Int? = null,
        val errorMessage: String? = null
    ) {
        val usedToken: Boolean get() = authenticated || tokenRejected
    }

    /**
     * Validate [apiToken] against the device and, on success, read capabilities + status.
     * Makes only read-only calls; never changes device state.
     */
    fun login(baseUrl: String, apiToken: String?): LoginResult {
        if (apiToken.isNullOrBlank()) {
            return LoginResult(
                authenticated = false,
                baseUrl = baseUrl.trimEnd('/'),
                capabilities = CapabilityMap.unknown(false),
                status = DeviceStatus(reachable = false, apiShapeConfirmed = false, limitedMode = true),
                errorMessage = "API token required for authenticated session"
            )
        }

        val discovery = client.discover(baseUrl, apiToken)
        if (!discovery.reachable) {
            return LoginResult(
                authenticated = false,
                baseUrl = discovery.baseUrl,
                capabilities = CapabilityMap.unknown(false),
                status = DeviceStatus(
                    reachable = false,
                    apiShapeConfirmed = false,
                    limitedMode = true,
                    identityHint = discovery.identityHint
                ),
                httpStatus = discovery.httpStatus,
                errorMessage = discovery.errorMessage ?: "Device unreachable"
            )
        }

        // Validate the token with a documented, harmless read-only call.
        val authResp = client.postApi(
            baseUrl,
            PineappleCandidates.LIST_NOTIFICATIONS.toProbeRequest(apiToken)
        )
        if (authResp.httpStatus == null) {
            return LoginResult(
                authenticated = false,
                baseUrl = discovery.baseUrl,
                capabilities = CapabilityMap.unknown(false),
                status = DeviceStatus(
                    reachable = true,
                    apiShapeConfirmed = false,
                    limitedMode = true,
                    identityHint = discovery.identityHint
                ),
                errorMessage = authResp.errorMessage ?: "No response from /api/"
            )
        }
        if (isTokenRejected(authResp)) {
            return LoginResult(
                authenticated = false,
                baseUrl = discovery.baseUrl,
                capabilities = CapabilityMap.unknown(apiShapeConfirmed = true),
                status = DeviceStatus(
                    reachable = true,
                    apiShapeConfirmed = true,
                    limitedMode = true,
                    identityHint = discovery.identityHint
                ),
                tokenRejected = true,
                httpStatus = authResp.httpStatus,
                errorMessage = "API token rejected by device"
            )
        }
        if (!authResp.isJson && !authResp.ok) {
            return LoginResult(
                authenticated = false,
                baseUrl = discovery.baseUrl,
                capabilities = CapabilityMap.unknown(false),
                status = DeviceStatus(
                    reachable = true,
                    apiShapeConfirmed = false,
                    limitedMode = true,
                    identityHint = discovery.identityHint
                ),
                httpStatus = authResp.httpStatus,
                errorMessage = "Unexpected /api/ reply; token may be required"
            )
        }

        // Token accepted: probe capabilities and read status (read-only throughout).
        val caps = probe.probe(baseUrl, apiToken)
        val status = fetchStatus(baseUrl, apiToken, caps, discovery.identityHint)
        return LoginResult(
            authenticated = true,
            baseUrl = discovery.baseUrl,
            capabilities = caps,
            status = status,
            httpStatus = authResp.httpStatus
        )
    }

    /**
     * Read device/firmware/version/connection from the live API. Tries read-only version
     * candidates; gracefully degrades to a minimal snapshot on unknown/limited firmware.
     */
    fun fetchStatus(
        baseUrl: String,
        apiToken: String?,
        capabilities: CapabilityMap,
        identityHint: String? = null
    ): DeviceStatus {
        val hints = linkedMapOf<String, String>()
        var firmware: String? = null
        var model: String? = null
        var version: String? = null
        var uptime: Long? = null

        for (candidate in PineappleCandidates.VERSION_CANDIDATES) {
            val resp = client.postApi(baseUrl, candidate.toProbeRequest(apiToken))
            if (!FirmwareProbe.responseSuggestsActionExists(resp)) continue
            val obj = PineappleJson.parseObject(resp.body) ?: continue
            if (firmware == null) firmware = PineappleJson.extractVersionHint(resp.body)
            if (model == null) model = PineappleJson.findString(obj, "model", "deviceModel", "hardware", "device")
            if (version == null) {
                version = PineappleJson.findString(obj, "version", "systemVersion", "firmwareVersion", "release", "build")
            }
            if (uptime == null) {
                val up = PineappleJson.findString(obj, "uptime", "uptimeSeconds", "uptime_seconds")
                uptime = up?.toLongOrNull()
            }
            firmware?.let { hints[candidate.key()] = it }
        }

        return DeviceStatus(
            reachable = true,
            apiShapeConfirmed = capabilities.apiShapeConfirmed,
            limitedMode = capabilities.limitedMode,
            firmware = firmware ?: capabilities.firmwareLabel.takeIf { it != "unknown" && it != "unreachable_or_unsupported" },
            model = model,
            version = version ?: firmware,
            uptimeSeconds = uptime,
            identityHint = identityHint,
            rawHints = hints
        )
    }

    companion object {
        /**
         * True when a device reply indicates an invalid/expired/missing token. A valid
         * read-only reply (e.g. listNotifications) never mentions a token.
         */
        fun isTokenRejected(resp: PineappleClient.ApiResponse): Boolean {
            if (resp.httpStatus == 401 || resp.httpStatus == 403) return true
            val b = resp.body?.lowercase().orEmpty()
            if (b.isBlank()) return false
            if ("invalid token" in b || "invalid api token" in b || "unauthorized" in b ||
                "token required" in b || "missing token" in b || "expired token" in b
            ) return true
            // Generic error mentioning a token.
            return ("error" in b || "false" in b) && ("apitoken" in b || "api token" in b || "\"token\"" in b)
        }
    }
}

/**
 * In-memory holder for the current authenticated session so the UI and the command-log
 * view share one source of truth. The apiToken is NOT held here — it stays in the
 * encrypted [Hak5ConfigStore] and is read on demand.
 */
object PineappleSessionManager {
    @Volatile
    var current: ActiveSession? = null
        private set

    data class ActiveSession(
        val baseUrl: String,
        val capabilities: CapabilityMap,
        val status: PineappleSession.DeviceStatus,
        val loginAtMs: Long,
        val catalog: ModuleCatalog? = null
    )

    fun start(result: PineappleSession.LoginResult, catalog: ModuleCatalog? = null): ActiveSession? {
        if (!result.authenticated) return null
        val s = ActiveSession(
            baseUrl = result.baseUrl,
            capabilities = result.capabilities,
            status = result.status,
            loginAtMs = System.currentTimeMillis(),
            catalog = catalog
        )
        current = s
        return s
    }

    fun updateCatalog(catalog: ModuleCatalog) {
        current = current?.copy(catalog = catalog)
    }

    fun isAuthenticated(): Boolean = current != null

    fun logout() {
        current = null
    }
}
