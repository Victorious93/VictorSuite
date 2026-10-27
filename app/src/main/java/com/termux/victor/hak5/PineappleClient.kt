package com.termux.victor.hak5

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OkHttp client for WiFi Pineapple Tetra 6th-gen management API over Wi-Fi/LAN.
 *
 * Phase 1: reachability / identity detection and read-only capability probes only.
 * No offensive actions. Cleartext HTTP to the management IP is expected on the
 * Pineapple AP (usesCleartextTraffic is already enabled app-wide).
 *
 * Injectable [OkHttpClient] for unit tests with MockWebServer.
 */
class PineappleClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val connectTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
        }

        fun apiUrl(baseUrl: String): String {
            val root = baseUrl.trimEnd('/')
            return "$root/api/"
        }

        fun rootUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/"
    }

    private fun clientWithTimeouts(): OkHttpClient {
        if (connectTimeoutMs == DEFAULT_TIMEOUT_MS && readTimeoutMs == DEFAULT_TIMEOUT_MS) {
            return httpClient
        }
        return httpClient.newBuilder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Reachability / identity check only. Tries GET/HEAD on the management root,
     * then a harmless documented POST to /api/ (listNotifications) when a token
     * is available. Does not change device state.
     */
    fun discover(baseUrl: String, apiToken: String? = null): DiscoveryResult {
        val start = System.currentTimeMillis()
        val client = clientWithTimeouts()
        val root = rootUrl(baseUrl)

        // 1) Light GET on management root — HTML UI is fine as reachability proof.
        try {
            val getReq = Request.Builder().url(root).get().header("Accept", "*/*").build()
            client.newCall(getReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val hint = identityFromHtmlOrHeaders(body, resp.header("Server"))
                if (resp.isSuccessful || resp.code in 300..499) {
                    // 401/403 still means something is listening on the management port.
                    return DiscoveryResult(
                        reachable = true,
                        baseUrl = baseUrl.trimEnd('/'),
                        httpStatus = resp.code,
                        identityHint = hint,
                        elapsedMs = System.currentTimeMillis() - start
                    )
                }
            }
        } catch (e: Exception) {
            // Fall through to API probe / failure.
            val apiAttempt = tryApiProbe(client, baseUrl, apiToken, start)
            if (apiAttempt != null) return apiAttempt
            return DiscoveryResult(
                reachable = false,
                baseUrl = baseUrl.trimEnd('/'),
                errorMessage = e.message ?: e.javaClass.simpleName,
                elapsedMs = System.currentTimeMillis() - start
            )
        }

        val apiAttempt = tryApiProbe(client, baseUrl, apiToken, start)
        if (apiAttempt != null) return apiAttempt

        return DiscoveryResult(
            reachable = false,
            baseUrl = baseUrl.trimEnd('/'),
            errorMessage = "No response from management root or /api/",
            elapsedMs = System.currentTimeMillis() - start
        )
    }

    private fun tryApiProbe(
        client: OkHttpClient,
        baseUrl: String,
        apiToken: String?,
        start: Long
    ): DiscoveryResult? {
        return try {
            val response = postApi(
                client,
                baseUrl,
                PineappleCandidates.LIST_NOTIFICATIONS.toProbeRequest(apiToken)
            )
            if (response.httpStatus != null) {
                DiscoveryResult(
                    reachable = true,
                    baseUrl = baseUrl.trimEnd('/'),
                    httpStatus = response.httpStatus,
                    identityHint = if (PineappleJson.looksLikeApiJson(response.body)) {
                        "6th-gen /api/ JSON reply"
                    } else {
                        "HTTP ${response.httpStatus} from /api/"
                    },
                    elapsedMs = System.currentTimeMillis() - start
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    data class ApiResponse(
        val httpStatus: Int?,
        val body: String?,
        val errorMessage: String? = null,
        val elapsedMs: Long = 0L
    ) {
        val isJson: Boolean get() = PineappleJson.looksLikeApiJson(body)
        val ok: Boolean get() = httpStatus != null && httpStatus in 200..299
    }

    fun postApi(baseUrl: String, request: PineappleApiRequest): ApiResponse {
        return postApi(clientWithTimeouts(), baseUrl, request)
    }

    private fun postApi(
        client: OkHttpClient,
        baseUrl: String,
        request: PineappleApiRequest
    ): ApiResponse {
        val start = System.currentTimeMillis()
        val url = apiUrl(baseUrl)
        val body = request.toJson().toRequestBody(JSON)
        val httpReq = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return try {
            client.newCall(httpReq).execute().use { resp ->
                ApiResponse(
                    httpStatus = resp.code,
                    body = resp.body?.string(),
                    elapsedMs = System.currentTimeMillis() - start
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                httpStatus = null,
                body = null,
                errorMessage = e.message ?: e.javaClass.simpleName,
                elapsedMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun identityFromHtmlOrHeaders(body: String, serverHeader: String?): String? {
        val lower = body.lowercase()
        return when {
            "wifi pineapple" in lower || "wifipineapple" in lower -> "WiFi Pineapple UI"
            "pineapple" in lower && ("hak5" in lower || "tetra" in lower || "nano" in lower) ->
                "Pineapple management UI"
            !serverHeader.isNullOrBlank() -> "Server: $serverHeader"
            body.isNotBlank() -> "HTTP management endpoint responding"
            else -> null
        }
    }
}
