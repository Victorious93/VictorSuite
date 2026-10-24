package com.termux.zerocore.remotecontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeStatusTest {

    @Test
    fun nodeStatusGetReturnsFrozenSnapshotShape() {
        val clock = MutableClock()
        val r = Fx.dispatcher(clock = clock).handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(operation = FleetProtocol.OP_NODE_STATUS_GET, actionRef = null))
        assertTrue(r.toJson(), r.ok)
        val s = r.result!!
        assertEquals(setOf("nodeId", "packageName", "connectionClass", "updatedAtMs", "domains"), s.keySet())
        assertEquals(Fx.NODE_ID, s.get("nodeId").asString)
        assertEquals("com.victorious.suite", s.get("packageName").asString)
        assertEquals("TERMUX_IPC", s.get("connectionClass").asString)
        assertEquals(clock.now, s.get("updatedAtMs").asLong)
        val domains = s.getAsJsonArray("domains").map { it.asJsonObject }
        domains.forEach { d ->
            assertTrue(d.keySet().all { it in setOf("name", "state", "detail") })
            assertTrue(d.get("name").asString in setOf("mesh", "vpn", "browser", "sms", "node_health", "other"))
            assertTrue(d.get("state").asString in setOf("ok", "degraded", "down", "unknown", "off"))
            d.get("detail")?.let { assertTrue(it.asString.length <= 200) }
        }
        assertEquals("node_health", domains[0].get("name").asString)
        val other = domains.single { it.get("name").asString == "other" }
        assertEquals("proot: debian stopped, kali running", other.get("detail").asString)
        assertEquals("ok", other.get("state").asString)
    }

    @Test
    fun nodeStatusIsNeverConfirmationGatedEvenIfDestructiveFlagSet() {
        val r = Fx.dispatcher().handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(operation = FleetProtocol.OP_NODE_STATUS_GET, actionRef = null, destructive = true))
        assertTrue(r.toJson(), r.ok)
    }

    @Test
    fun nodeIdArgMustMatchAndUnknownArgsAreIgnored() {
        val d = Fx.dispatcher()
        Fx.assertError(d.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(operation = FleetProtocol.OP_NODE_STATUS_GET, actionRef = null) {
            it.add("args", Fx.json("""{"nodeId":"mesh-${"a".repeat(32)}"}"""))
        }), RemoteErrorCategory.NOT_FOUND)
        assertTrue(d.handleRemote(Fx.DCA_PKG, Fx.DCA_CERT, Fx.blob(operation = FleetProtocol.OP_NODE_STATUS_GET, actionRef = null) {
            it.add("args", Fx.json("""{"nodeId":"${Fx.NODE_ID}","futureKey":"x"}"""))
        }).ok)
    }

    @Test
    fun snapshotContainsNoSecretsOrHistory() {
        val raw = NodeStatusSnapshotCodec.encode(Fx.snapshot(1L)).lowercase()
        listOf("history", "password", "token", "secret", "api_key", "apikey", "export ", "ssh", "path=", "home=", "env").forEach {
            assertFalse("snapshot leaked '$it': $raw", raw.contains(it))
        }
    }

    @Test
    fun codecRefusesSecretOrHistoryInDetail() {
        listOf("token=abc", "OPENAI_API_KEY set", "last cmd from .bash_history", "password ok", "Bearer x").forEach { bad ->
            Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
                NodeStatusSnapshotCodec.encode(
                    NodeStatusSnapshotCodec.buildTermuxSnapshot(Fx.NODE_ID, Fx.NODE_PKG, 1L, StatusTrayState.OK, bad),
                )
            }
        }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
            NodeStatusSnapshotCodec.encode(NodeStatusSnapshotCodec.buildTermuxSnapshot(Fx.NODE_ID, Fx.NODE_PKG, 1L, StatusTrayState.OK, "x".repeat(201)))
        }
    }

    @Test
    fun containerSummaryUsesOtherDomainAndSanitisesNames() {
        assertEquals(StatusTrayState.OFF to "proot: none installed", ContainerSummary.summarize(emptyList()))
        assertEquals(StatusTrayState.UNKNOWN, ContainerSummary.summarize(null).first)
        val (state, detail) = ContainerSummary.summarize(listOf(ContainerInfo("kali\$(rm -rf)", false)))
        assertEquals(StatusTrayState.OFF, state)
        assertEquals("proot: kalirm-rf stopped", detail)
        val many = ContainerSummary.summarize((1..40).map { ContainerInfo("distro$it", true) }).second
        assertTrue(many.length <= 200)
    }

    @Test
    fun healthyAliasFollowsSpec() {
        assertEquals(true, Fx.snapshot(1L).healthy)
        val down = NodeStatusSnapshotCodec.buildTermuxSnapshot(Fx.NODE_ID, null, 1L, StatusTrayState.DOWN)
        assertEquals(false, down.healthy)
        val unknown = NodeStatusSnapshotCodec.buildTermuxSnapshot(Fx.NODE_ID, null, 1L, StatusTrayState.UNKNOWN)
        assertNull(unknown.healthy)
    }
}
