package com.termux.victor.hak5

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

class PineappleModuleListerTest {
    private lateinit var server: MockWebServer
    private lateinit var lister: PineappleModuleLister

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val ok = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        val client = PineappleClient(httpClient = ok, connectTimeoutMs = 2000, readTimeoutMs = 2000)
        lister = PineappleModuleLister(client)
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

    private fun confirmedCaps(): CapabilityMap = CapabilityMap(
        firmwareLabel = "2.7.0",
        apiShapeConfirmed = true,
        confirmedActions = setOf(
            PineappleCandidates.LIST_NOTIFICATIONS.key(),
            "system:modules:listModules"
        ),
        limitedMode = false
    )

    private fun moduleDispatcher(body: String): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/api/" && request.path != "/api") {
                    return MockResponse().setResponseCode(404)
                }
                val reqBody = request.body.readUtf8()
                return when {
                    "listModules" in reqBody ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json").setBody(body)
                    else ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(fixture("unknown_action.json"))
                }
            }
        }
    }

    @Test
    fun list_parsesObjectModulesAndActions() {
        server.dispatcher = moduleDispatcher(fixture("module_list_v1.json"))
        val catalog = lister.list(baseUrl(), "tok", confirmedCaps())
        assertFalse(catalog.isEmpty)
        assertEquals(4, catalog.modules.size)
        val pineap = catalog.modules.first { it.name == "PineAP" }
        val enable = pineap.actions.first { it.action == "enablePineAP" }
        assertEquals(Hak5ActionSeverity.POTENTIALLY_INVASIVE, enable.severity)
        val status = pineap.actions.first { it.action == "getStatus" }
        assertEquals(Hak5ActionSeverity.READ_ONLY, status.severity)
    }

    @Test
    fun list_parsesNameOnlyArray() {
        server.dispatcher = moduleDispatcher(fixture("module_list_names.json"))
        val catalog = lister.list(baseUrl(), "tok", confirmedCaps())
        assertEquals(4, catalog.modules.size)
        assertNotNull(catalog.modules.firstOrNull { it.name == "Dashboard" })
        assertTrue(catalog.modules.all { it.actions.isEmpty() })
    }

    @Test
    fun list_limitedWhenApiShapeUnconfirmed_noNetwork() {
        val caps = CapabilityMap.unknown(apiShapeConfirmed = false)
        val catalog = lister.list(baseUrl(), "tok", caps)
        assertTrue(catalog.limited)
        assertTrue(catalog.isEmpty)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun list_limitedWhenFirmwareUnknown() {
        // API shape confirmed, but the device rejects every module-list candidate.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(fixture("unknown_action.json"))
            }
        }
        val caps = CapabilityMap.unknown(apiShapeConfirmed = true)
        val catalog = lister.list(baseUrl(), "tok", caps)
        assertTrue(catalog.limited)
        assertTrue(catalog.isEmpty)
    }
}
