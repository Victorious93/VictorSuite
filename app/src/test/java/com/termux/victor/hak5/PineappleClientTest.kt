package com.termux.victor.hak5

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PineappleClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: PineappleClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val ok = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        client = PineappleClient(httpClient = ok, connectTimeoutMs = 2000, readTimeoutMs = 2000)
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

    @Test
    fun buildBaseUrl_defaultPineapple() {
        assertEquals("http://172.16.42.1:1471", Hak5ConfigStore.buildBaseUrl("172.16.42.1", 1471))
        assertEquals("http://10.0.0.1:1471", Hak5ConfigStore.buildBaseUrl("10.0.0.1/", 1471))
    }

    @Test
    fun apiUrl_appendsApiSlash() {
        assertEquals("http://172.16.42.1:1471/api/", PineappleClient.apiUrl("http://172.16.42.1:1471"))
        assertEquals("http://172.16.42.1:1471/api/", PineappleClient.apiUrl("http://172.16.42.1:1471/"))
    }

    @Test
    fun discover_reachableFromHtmlUi() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fixture("management_ui.html"))
        )
        val result = client.discover(baseUrl())
        assertTrue(result.reachable)
        assertEquals(200, result.httpStatus)
        assertNotNull(result.identityHint)
        assertTrue(result.identityHint!!.contains("Pineapple", ignoreCase = true))
    }

    @Test
    fun discover_unreachable() {
        server.shutdown()
        // Point at a closed port on loopback.
        val result = client.discover("http://127.0.0.1:1")
        assertFalse(result.reachable)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun postApi_sendsJsonBodyWithSystemAndAction() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture("list_notifications_ok.json"))
        )
        val resp = client.postApi(
            baseUrl(),
            PineappleApiRequest(
                system = "notifications",
                action = "listNotifications",
                apiToken = "secret-token-value"
            )
        )
        assertTrue(resp.ok)
        assertTrue(resp.isJson)
        val recorded = server.takeRequest()
        assertEquals("/api/", recorded.path)
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"system\":\"notifications\""))
        assertTrue(body.contains("\"action\":\"listNotifications\""))
        assertTrue(body.contains("\"apiToken\":\"secret-token-value\""))
    }

    @Test
    fun request_rejectsBothSystemAndModule() {
        try {
            PineappleApiRequest(system = "a", module = "b", action = "x")
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
