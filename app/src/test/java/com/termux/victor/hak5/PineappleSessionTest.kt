package com.termux.victor.hak5

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PineappleSessionTest {
    private lateinit var server: MockWebServer
    private lateinit var session: PineappleSession

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val ok = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        val client = PineappleClient(httpClient = ok, connectTimeoutMs = 2000, readTimeoutMs = 2000)
        session = PineappleSession(client)
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

    private fun dispatcher(notificationsBody: String): Dispatcher {
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
                            .setHeader("Content-Type", "application/json").setBody(notificationsBody)
                    "getDeviceStatus" in body ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(fixture("device_status_v1.json"))
                    else ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(fixture("unknown_action.json"))
                }
            }
        }
    }

    @Test
    fun login_success_withValidToken() {
        server.dispatcher = dispatcher(fixture("list_notifications_ok.json"))
        val result = session.login(baseUrl(), "valid-token")
        assertTrue(result.authenticated)
        assertFalse(result.tokenRejected)
        assertTrue(result.capabilities.apiShapeConfirmed)
        assertEquals("2.7.0", result.status.firmware)
        assertEquals("TETRA", result.status.model)
    }

    @Test
    fun login_failure_tokenRejected() {
        server.dispatcher = dispatcher(fixture("invalid_token.json"))
        val result = session.login(baseUrl(), "bad-token")
        assertFalse(result.authenticated)
        assertTrue(result.tokenRejected)
        // API shape is still confirmed — the route exists, the token is what failed.
        assertTrue(result.status.apiShapeConfirmed)
    }

    @Test
    fun login_failure_blankToken_noNetwork() {
        val result = session.login(baseUrl(), "")
        assertFalse(result.authenticated)
        assertFalse(result.tokenRejected)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun login_failure_unreachable() {
        server.shutdown()
        val result = session.login("http://127.0.0.1:1", "tok")
        assertFalse(result.authenticated)
        assertNull(result.status.firmware)
    }

    @Test
    fun fetchStatus_parsesFirmwareModelUptime() {
        server.dispatcher = dispatcher(fixture("list_notifications_ok.json"))
        val caps = CapabilityMap(
            firmwareLabel = "2.7.0",
            apiShapeConfirmed = true,
            confirmedActions = setOf(PineappleCandidates.LIST_NOTIFICATIONS.key()),
            limitedMode = false
        )
        val status = session.fetchStatus(baseUrl(), "tok", caps)
        assertEquals("2.7.0", status.firmware)
        assertEquals("TETRA", status.model)
        assertEquals(3600L, status.uptimeSeconds)
    }
}
