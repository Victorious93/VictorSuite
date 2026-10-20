package com.termux.zerocore.hak5

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class FirmwareProbeTest {
    private lateinit var server: MockWebServer
    private lateinit var probe: FirmwareProbe

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val ok = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        val client = PineappleClient(httpClient = ok, connectTimeoutMs = 2000, readTimeoutMs = 2000)
        probe = FirmwareProbe(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("hak5_fixtures/$name")
            ?: error("missing fixture $name")
        return stream.bufferedReader().readText()
    }

    private fun jsonDispatcher(
        notificationsBody: String = fixture("list_notifications_ok.json"),
        versionBody: String? = fixture("device_status_v1.json"),
        unknownBody: String = fixture("unknown_action.json")
    ): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/" || request.path == "") {
                    return MockResponse().setResponseCode(200).setBody(fixture("management_ui.html"))
                }
                if (request.path != "/api/" && request.path != "/api") {
                    return MockResponse().setResponseCode(404)
                }
                val body = request.body.readUtf8()
                return when {
                    "listNotifications" in body ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(notificationsBody)
                    "getDeviceStatus" in body && versionBody != null ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(versionBody)
                    else ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(unknownBody)
                }
            }
        }
    }

    @Test
    fun probe_knownFirmware_confirmsApiAndVersion() {
        server.dispatcher = jsonDispatcher()
        val caps = probe.probe(baseUrl(), apiToken = "test-token")
        assertTrue(caps.apiShapeConfirmed)
        assertFalse(caps.limitedMode)
        assertEquals("2.7.0", caps.firmwareLabel)
        assertTrue(caps.confirmedActions.contains(PineappleCandidates.LIST_NOTIFICATIONS.key()))
        assertTrue(
            caps.confirmedActions.any { it.contains("getDeviceStatus") }
        )
    }

    @Test
    fun probe_unknownFirmware_limitedMode() {
        // API shape OK via notifications, but every version candidate returns unknown action.
        server.dispatcher = jsonDispatcher(versionBody = null)
        val caps = probe.probe(baseUrl(), apiToken = "test-token")
        assertTrue(caps.apiShapeConfirmed)
        assertTrue(caps.limitedMode)
        assertEquals("unknown", caps.firmwareLabel)
        assertTrue(caps.confirmedActions.contains(PineappleCandidates.LIST_NOTIFICATIONS.key()))
    }

    @Test
    fun probe_noApiShape_unreachableOrUnsupported() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(200).setBody("<html>not pineapple api</html>")
            }
        }
        val caps = probe.probe(baseUrl(), apiToken = null)
        assertFalse(caps.apiShapeConfirmed)
        assertTrue(caps.limitedMode)
    }

    @Test
    fun connect_recordsReachableAndCapabilities() {
        server.dispatcher = jsonDispatcher()
        val result = probe.connect(baseUrl(), "tok")
        assertTrue(result.discovery.reachable)
        assertTrue(result.capabilities.apiShapeConfirmed)
        assertEquals("2.7.0", result.capabilities.firmwareLabel)
        assertTrue(result.usedToken)
    }

    @Test
    fun responseSuggestsActionExists_rejectsInvalidToken() {
        val resp = PineappleClient.ApiResponse(
            httpStatus = 200,
            body = fixture("invalid_token.json")
        )
        assertFalse(FirmwareProbe.responseSuggestsActionExists(resp))
    }

    @Test
    fun extractVersionHint_fromFixture() {
        val hint = PineappleJson.extractVersionHint(fixture("device_status_v1.json"))
        assertEquals("2.7.0", hint)
    }
}
