package com.termux.victor.remotecontrol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class MutableClock(var now: Long = 1_760_920_000_000L) : RemoteClock {
    override fun nowMs(): Long = now
}

class FakeConfigSource(
    var files: Map<String, String> = emptyMap(),
    var rc: RemoteControlConfig = RemoteControlConfig(),
) : NodeConfigSource {
    override fun files(): Map<String, String> = files
    override fun remoteControlConfig(): RemoteControlConfig = rc
}

class RecordingSink : NodeConfigSink {
    var termux: Map<String, String>? = null
    var colors: Map<String, String>? = null
    var rc: RemoteControlConfig? = null
    val writes get() = listOfNotNull(termux, colors, rc).size
    override fun writeTermuxProperties(values: Map<String, String>) { termux = values }
    override fun writeColorsProperties(values: Map<String, String>) { colors = values }
    override fun saveRemoteControlConfig(config: RemoteControlConfig) { rc = config }
}

object Fx {
    const val NODE_ID = "victorsuite-0123456789abcdef0123456789abcdef"
    const val NODE_PKG = "com.termux"
    const val DCA_PKG = "ai.droidcommand.app"
    const val DCA_REF = "controller"
    const val DCA_CERT = "AA:BB:CC:DD"

    private var counter = 0

    fun nonce(): String {
        counter++
        return "%032x".format(counter)
    }

    fun blob(
        operation: String? = FleetProtocol.OP_REMOTE_COMMAND,
        actionRef: String? = "node.ping",
        nonce: String = nonce(),
        timestampMs: Long = MutableClock().now,
        ttlMs: Long? = null,
        target: String = NODE_ID,
        destructive: Boolean? = null,
        extra: (JsonObject) -> Unit = {},
    ): String = JsonObject().apply {
        operation?.let { addProperty("operation", it) }
        addProperty("requestId", "req-$nonce")
        addProperty("targetNodeRef", target)
        actionRef?.let { addProperty("actionRef", it) }
        addProperty("nonce", nonce)
        addProperty("timestampMs", timestampMs)
        ttlMs?.let { addProperty("ttlMs", it) }
        destructive?.let { addProperty("destructive", it) }
        extra(this)
    }.toString()

    fun pairedConfig(externalControl: Boolean = true, cert: String? = DCA_CERT) = RemoteControlConfig(
        externalControlEnabled = externalControl,
        pairings = listOf(DevicePairing(DCA_REF, DCA_PKG, cert)),
    )

    fun snapshot(now: Long) = NodeStatusSnapshotCodec.buildTermuxSnapshot(
        nodeId = NODE_ID, packageName = NODE_PKG, updatedAtMs = now,
        nodeHealth = StatusTrayState.OK, nodeHealthDetail = "bootstrap ok, storage ok",
        containerState = StatusTrayState.OK,
        containerDetail = ContainerSummary.summarize(listOf(ContainerInfo("kali", true), ContainerInfo("debian", false))).second,
    )

    fun dispatcher(
        cfg: RemoteControlConfig = pairedConfig(),
        clock: MutableClock = MutableClock(),
        source: NodeConfigSource? = FakeConfigSource(),
        sink: NodeConfigSink? = RecordingSink(),
    ) = RemoteEnvelopeDispatcher(
        nodeId = NODE_ID,
        nodePackage = NODE_PKG,
        config = { cfg },
        replayGuard = ReplayGuard(clock),
        clock = clock,
        statusProvider = { now -> snapshot(now) },
        configSource = source,
        configSink = sink,
    )

    fun json(raw: String): JsonObject = JsonParser.parseString(raw).asJsonObject

    fun assertError(resp: RemoteResponse, category: RemoteErrorCategory) {
        assertFalse("expected error ${category.wire} but was ok: ${resp.toJson()}", resp.ok)
        assertEquals(resp.toJson(), category, resp.errorCategory)
        assertEquals(category.wire, json(resp.toJson()).getAsJsonObject("error").get("category").asString)
    }

    fun expectCategory(category: RemoteErrorCategory, block: () -> Unit) {
        try {
            block()
        } catch (e: RemoteControlException) {
            assertEquals(e.message, category, e.category)
            return
        }
        throw AssertionError("expected ${category.wire}")
    }
}
