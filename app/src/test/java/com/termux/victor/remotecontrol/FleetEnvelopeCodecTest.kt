package com.termux.zerocore.remotecontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetEnvelopeCodecTest {

    @Test
    fun parsesFrozenTopLevelFields() {
        val e = FleetEnvelopeCodec.parse(Fx.blob(nonce = "a".repeat(32), destructive = true) {
            it.add("params", Fx.json("""{"k":"v","n":3,"b":true}"""))
            it.add("trigger", Fx.json("""{"sourceNumber":"+15555550100","matchedKeyword":true,"rawExtras":{}}"""))
        })
        assertEquals(FleetProtocol.OP_REMOTE_COMMAND, e.operation)
        assertEquals("node.ping", e.actionRef)
        assertEquals(mapOf("k" to "v", "n" to "3", "b" to "true"), e.params)
        assertEquals("a".repeat(32), e.nonce)
        assertTrue(e.destructive)
        assertTrue(e.triggerJson!!.contains("matchedKeyword"))
    }

    @Test
    fun ttlDefaultsTo120000() {
        assertEquals(120_000L, FleetEnvelopeCodec.parse(Fx.blob(ttlMs = null)).ttlMs)
    }

    @Test
    fun ttlAtMaxAccepted() {
        assertEquals(300_000L, FleetEnvelopeCodec.parse(Fx.blob(ttlMs = 300_000)).ttlMs)
    }

    @Test
    fun ttlAboveMaxIsInvalidParameters() {
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse(Fx.blob(ttlMs = 300_001)) }
    }

    @Test
    fun ttlZeroOrNegativeIsInvalidParameters() {
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse(Fx.blob(ttlMs = 0)) }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse(Fx.blob(ttlMs = -5)) }
    }

    @Test
    fun nonceMustBe128Bit() {
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse(Fx.blob(nonce = "a".repeat(31))) }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse(Fx.blob(nonce = "a".repeat(33))) }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse(Fx.blob(nonce = "z".repeat(32))) }
        assertTrue(FleetProtocol.isValidNonce("AAAAAAAAAAAAAAAAAAAAAA")) // 16 bytes, base64 unpadded
        assertFalse(FleetProtocol.isValidNonce("AAAAAAAAAAAAAAAAAAAA")) // 15 bytes
    }

    @Test
    fun missingRequiredFieldsAreInvalid() {
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse("""{"targetNodeRef":"x","nonce":"${"a".repeat(32)}","timestampMs":1}""") }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse(Fx.blob(actionRef = null)) }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse("not json") }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { FleetEnvelopeCodec.parse(null) }
    }

    @Test
    fun nestedAuthIsRejected() {
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
            FleetEnvelopeCodec.parse(Fx.blob { it.add("auth", Fx.json("{}")) })
        }
    }

    @Test
    fun unknownExplicitOperationIsUnsupportedOp() {
        Fx.expectCategory(RemoteErrorCategory.UNSUPPORTED_OP) { FleetEnvelopeCodec.parse(Fx.blob(operation = "device.wipe")) }
    }

    @Test
    fun argsIsAcceptedAsAliasOfParams() {
        val e = FleetEnvelopeCodec.parse(Fx.blob { it.add("args", Fx.json("""{"a":"1"}""")) })
        assertEquals(mapOf("a" to "1"), e.params)
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
            FleetEnvelopeCodec.parse(Fx.blob {
                it.add("args", Fx.json("""{"a":"1"}"""))
                it.add("params", Fx.json("""{"a":"2"}"""))
            })
        }
    }

    @Test
    fun nestedParamValuesAreInvalid() {
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
            FleetEnvelopeCodec.parse(Fx.blob { it.add("params", Fx.json("""{"a":{"b":1}}""")) })
        }
    }

    @Test
    fun operationIsInferredWhenAbsent() {
        assertEquals(FleetProtocol.OP_NODE_STATUS_GET, FleetEnvelopeCodec.parse(Fx.blob(operation = null, actionRef = "node.status.get")).operation)
        assertEquals(FleetProtocol.OP_REMOTE_COMMAND, FleetEnvelopeCodec.parse(Fx.blob(operation = null, actionRef = "node.ping")).operation)
        assertEquals(FleetProtocol.OP_MACRO_RUN, FleetEnvelopeCodec.parse(Fx.blob(operation = null, actionRef = "m") {
            it.add("steps", com.google.gson.JsonParser.parseString("""[{"op":"node.ping"}]"""))
        }).operation)
    }

    @Test
    fun macroStepFields() {
        val e = FleetEnvelopeCodec.parse(Fx.blob(operation = FleetProtocol.OP_MACRO_STEP, actionRef = null) {
            it.addProperty("op", "node.ping")
            it.addProperty("macroId", "m1")
            it.addProperty("stepIndex", 2)
            it.addProperty("onError", "continue")
        })
        assertEquals("node.ping", e.stepOp)
        assertEquals("m1", e.macroId)
        assertEquals(2, e.stepIndex)
        assertEquals("continue", e.onError)
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
            FleetEnvelopeCodec.parse(Fx.blob(operation = FleetProtocol.OP_MACRO_STEP) { it.addProperty("onError", "retry") })
        }
    }

    @Test
    fun macroRunFields() {
        val e = FleetEnvelopeCodec.parse(Fx.blob(operation = FleetProtocol.OP_MACRO_RUN, actionRef = null) {
            it.addProperty("macroId", "m2")
            it.addProperty("dryRun", true)
            it.add("steps", com.google.gson.JsonParser.parseString("""[{"op":"node.ping","onError":"continue","timeoutMs":1000},{"op":"node.status.get"}]"""))
        })
        assertEquals("m2", e.macroId)
        assertTrue(e.dryRun)
        assertEquals(2, e.steps!!.size)
        assertEquals("continue", e.steps!![0].onError)
        assertEquals("abort", e.steps!![1].onError)
        assertNull(e.stepOp)
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
            FleetEnvelopeCodec.parse(Fx.blob(operation = FleetProtocol.OP_MACRO_RUN) { it.add("steps", com.google.gson.JsonArray()) })
        }
    }

    @Test
    fun encodeRoundTrips() {
        val e = FleetEnvelopeCodec.parse(Fx.blob { it.add("params", Fx.json("""{"x":"y"}""")) })
        assertEquals(e, FleetEnvelopeCodec.parse(FleetEnvelopeCodec.encode(e)))
    }
}
