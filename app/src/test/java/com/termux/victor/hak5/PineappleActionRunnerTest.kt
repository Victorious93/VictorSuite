package com.termux.zerocore.hak5

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PineappleActionRunnerTest {
    private lateinit var server: MockWebServer
    private lateinit var client: PineappleClient
    private val captured = mutableListOf<Hak5CommandLog.CommandLogEntry>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val ok = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        client = PineappleClient(httpClient = ok, connectTimeoutMs = 2000, readTimeoutMs = 2000)
        captured.clear()
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

    private fun runner(): PineappleActionRunner =
        PineappleActionRunner(client) { captured.add(it) }

    private val capKey = "module:PineAP:enablePineAP"

    private fun invasiveAction() = Hak5PendingAction(
        id = capKey,
        title = "PineAP · enablePineAP",
        summary = "Enable PineAP",
        severity = Hak5ActionSeverity.POTENTIALLY_INVASIVE,
        capabilityKey = capKey
    )

    private fun caps() = CapabilityMap(
        firmwareLabel = "2.7.0",
        apiShapeConfirmed = true,
        confirmedActions = setOf(capKey),
        limitedMode = false
    )

    private fun request() = PineappleApiRequest(
        module = "PineAP",
        action = "enablePineAP",
        apiToken = "secret-token",
        extra = mapOf("ssid" to "demo")
    )

    private fun okDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            return MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture("action_run_ok.json"))
        }
    }

    @Test
    fun run_blockedWithoutConfirmation_neverHitsNetwork() {
        server.dispatcher = okDispatcher()
        val outcome = runner().run(
            baseUrl(), "secret-token", invasiveAction(), request(),
            authorized = true, capabilities = caps(), userConfirmed = false
        )
        assertFalse(outcome.executed)
        assertTrue(outcome.blocked)
        assertNotNull(outcome.blockReason)
        assertEquals(0, server.requestCount)
        assertEquals(1, captured.size)
        assertTrue(captured[0].blocked)
        assertFalse(captured[0].executed)
    }

    @Test
    fun run_blockedWithoutAuthorization() {
        server.dispatcher = okDispatcher()
        val outcome = runner().run(
            baseUrl(), "secret-token", invasiveAction(), request(),
            authorized = false, capabilities = caps(), userConfirmed = true
        )
        assertFalse(outcome.executed)
        assertTrue(outcome.blocked)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun run_executesWhenAuthorizedConfirmedAndConfirmedOnFirmware() {
        server.dispatcher = okDispatcher()
        val outcome = runner().run(
            baseUrl(), "secret-token", invasiveAction(), request(),
            authorized = true, capabilities = caps(), userConfirmed = true
        )
        assertTrue(outcome.executed)
        assertFalse(outcome.blocked)
        assertTrue(outcome.success)
        assertEquals(1, server.requestCount)
        // Request carried the action; the sink captured an executed entry.
        val recorded = server.takeRequest()
        assertTrue(recorded.body.readUtf8().contains("enablePineAP"))
        assertEquals(1, captured.size)
        assertTrue(captured[0].executed)
    }

    @Test
    fun run_executedEntry_persistsAndRedactsToken() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Device echoes a token back in its reply — must never land on disk.
                return MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"success":true,"apiToken":"secret-token"}""")
            }
        }
        val dir = createTempDir(prefix = "hak5cmd")
        try {
            val persisting = PineappleActionRunner(client) { Hak5CommandLog.append(dir, it) }
            persisting.run(
                baseUrl(), "secret-token", invasiveAction(), request(),
                authorized = true, capabilities = caps(), userConfirmed = true
            )
            val raw = Hak5CommandLog.readRaw(dir)
            assertEquals(1, raw.size)
            assertFalse(raw[0].contains("secret-token"))
            assertTrue(raw[0].contains("***"))
            val entries = Hak5CommandLog.read(dir)
            assertTrue(entries[0].executed)
        } finally {
            dir.deleteRecursively()
        }
    }
}
