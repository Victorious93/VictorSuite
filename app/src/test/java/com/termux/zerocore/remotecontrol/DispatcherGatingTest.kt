package com.termux.zerocore.remotecontrol

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatcherGatingTest {
    private fun remote(d: RemoteEnvelopeDispatcher, blob: String, pkg: String? = Fx.DCA_PKG, cert: String? = null) =
        d.handleRemote(pkg, cert, blob)

    // --- trust pipeline order: caller-id -> external control -> envelope/replay -> policy ---

    @Test
    fun externalControlIsOffByDefault() {
        assertFalse(RemoteControlConfig().externalControlEnabled)
        val d = Fx.dispatcher(cfg = Fx.pairedConfig(externalControl = false))
        Fx.assertError(remote(d, Fx.blob(operation = FleetProtocol.OP_CAPABILITIES_GET, actionRef = null)), RemoteErrorCategory.EXTERNAL_CONTROL_DISABLED)
        Fx.assertError(remote(d, Fx.blob(operation = FleetProtocol.OP_NODE_STATUS_GET, actionRef = null)), RemoteErrorCategory.EXTERNAL_CONTROL_DISABLED)
    }

    @Test
    fun unpairedPackageIsNotPaired() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(), pkg = "com.evil.app"), RemoteErrorCategory.NOT_PAIRED)
    }

    @Test
    fun unauthenticatedCallerIsUnauthorized() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(), pkg = null), RemoteErrorCategory.UNAUTHORIZED)
    }

    @Test
    fun pinnedCertMismatchIsUnauthorized() {
        val d = Fx.dispatcher(cfg = Fx.pairedConfig(cert = "AA:BB"))
        Fx.assertError(remote(d, Fx.blob(), cert = "CCDD"), RemoteErrorCategory.UNAUTHORIZED)
        Fx.assertError(remote(d, Fx.blob(), cert = null), RemoteErrorCategory.UNAUTHORIZED)
        assertTrue(remote(d, Fx.blob(), cert = "aabb").ok)
    }

    @Test
    fun payloadSourceFieldsAreNotIdentity() {
        // An envelope claiming to be from a paired node does not help an unpaired caller.
        val blob = Fx.blob { it.addProperty("sourceNodeRef", Fx.DCA_REF); it.addProperty("packageName", Fx.DCA_PKG) }
        Fx.assertError(remote(Fx.dispatcher(), blob, pkg = "com.evil.app"), RemoteErrorCategory.NOT_PAIRED)
    }

    @Test
    fun replayIsKeyedByAuthenticatedSource() {
        val cfg = RemoteControlConfig(true, listOf(DevicePairing("controller", Fx.DCA_PKG), DevicePairing("victorlaunch", "com.victorious.launch")))
        val d = Fx.dispatcher(cfg = cfg)
        val n = Fx.nonce()
        assertTrue(remote(d, Fx.blob(nonce = n)).ok)
        Fx.assertError(remote(d, Fx.blob(nonce = n)), RemoteErrorCategory.REPLAY_DETECTED)
        assertTrue(remote(d, Fx.blob(nonce = n), pkg = "com.victorious.launch").ok)
    }

    @Test
    fun expiredAndSkewedRequestsAreRejected() {
        val clock = MutableClock()
        val d = Fx.dispatcher(clock = clock)
        Fx.assertError(remote(d, Fx.blob(timestampMs = clock.now - 300_000, ttlMs = 120_000)), RemoteErrorCategory.EXPIRED)
        Fx.assertError(remote(d, Fx.blob(timestampMs = clock.now + 200_000)), RemoteErrorCategory.EXPIRED)
    }

    @Test
    fun ttlAboveMaxIsInvalidParametersEndToEnd() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(ttlMs = 300_001)), RemoteErrorCategory.INVALID_PARAMETERS)
    }

    @Test
    fun wrongTargetIsNotFound() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(target = "victorsms-" + "1".repeat(32))), RemoteErrorCategory.NOT_FOUND)
        assertTrue(remote(Fx.dispatcher(), Fx.blob(target = Fx.NODE_PKG)).ok)
    }

    @Test
    fun requestIdIsEchoed() {
        val n = Fx.nonce()
        val r = Fx.json(remote(Fx.dispatcher(), Fx.blob(nonce = n)).toJson())
        assertEquals("req-$n", r.get("request_id").asString)
        assertEquals(1, r.get("protocol_major").asInt)
        assertEquals(Fx.NODE_ID, r.get("node_id").asString)
    }

    // --- capabilities.get ---

    @Test
    fun capabilitiesListsEveryOpWithDescriptor() {
        val r = remote(Fx.dispatcher(), Fx.blob(operation = FleetProtocol.OP_CAPABILITIES_GET, actionRef = null))
        assertTrue(r.toJson(), r.ok)
        assertEquals(1, r.result!!.get("protocolMajor").asInt)
        assertEquals(1, r.result!!.get("protocolMinor").asInt)
        val ops = r.result!!.getAsJsonArray("ops").associate { it.asJsonObject.get("name").asString to it.asJsonObject }
        assertEquals(FleetProtocol.KNOWN_OPS, ops.keys)
        ops.values.forEach { o ->
            assertTrue(o.get("kind").asString in setOf("read", "action", "destructive"))
            assertTrue(o.has("remoteAllowed") && o.has("requiresConfirm"))
        }
        val status = ops.getValue("node.status.get")
        assertEquals("read", status.get("kind").asString)
        assertTrue(status.get("remoteAllowed").asBoolean)
        assertFalse(status.get("requiresConfirm").asBoolean)
        assertFalse(ops.getValue("config.export").get("remoteAllowed").asBoolean)
        assertFalse(ops.getValue("config.import").get("remoteAllowed").asBoolean)
        assertTrue(ops.getValue("config.import").get("requiresConfirm").asBoolean)
        val actions = r.result!!.getAsJsonArray("actions").associate { it.asJsonObject.get("name").asString to it.asJsonObject }
        assertEquals("destructive", actions.getValue("shell.exec").get("kind").asString)
        assertTrue(actions.getValue("shell.exec").get("requiresConfirm").asBoolean)
    }

    // --- gating ---

    @Test
    fun readActionRuns() {
        val r = remote(Fx.dispatcher(), Fx.blob(actionRef = "node.ping"))
        assertTrue(r.toJson(), r.ok)
        assertTrue(r.result!!.getAsJsonObject("output").get("pong").asBoolean)
    }

    @Test
    fun destructiveActionIsConfirmationRequiredEvenWithForgedDestructiveFalse() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(actionRef = "shell.exec", destructive = false)), RemoteErrorCategory.CONFIRMATION_REQUIRED)
    }

    @Test
    fun remoteTriggerIsNeverAutoApproved() {
        val blob = Fx.blob(actionRef = "shell.exec", destructive = false) {
            it.add("params", Fx.json("""{"confirmed":"true","approved":"true","yolo":"true"}"""))
            it.add("trigger", Fx.json("""{"sourceNumber":"+15555550100","matchedKeyword":true,"rawExtras":{"approve":"true"}}"""))
            it.addProperty("confirmed", true)
        }
        Fx.assertError(remote(Fx.dispatcher(), blob), RemoteErrorCategory.CONFIRMATION_REQUIRED)
    }

    @Test
    fun shellAndExecActionRefsAreDestructiveByDefault() {
        listOf("shell.exec", "termux.shell", "exec", "run_command", "device.wipe", "power-off", "bootloader.unlock", "su.run").forEach {
            assertEquals(it, OpKind.DESTRUCTIVE, ActionClassifier.classify(it))
        }
        listOf("node.ping", "status.read", "summary").forEach { assertEquals(it, OpKind.ACTION, ActionClassifier.classify(it)) }
        // A registry that wrongly lists a shell action as READ is still raised to destructive.
        val d = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig() }, ReplayGuard(MutableClock()), MutableClock(),
            { Fx.snapshot(it) }, RemoteActionRegistry(listOf(RemoteAction(OpDescriptor("shell.exec", OpKind.READ, true, false)) { throw AssertionError("must not run") })))
        Fx.assertError(d.handleRemote(Fx.DCA_PKG, null, Fx.blob(actionRef = "shell.exec")), RemoteErrorCategory.CONFIRMATION_REQUIRED)
    }

    @Test
    fun unknownActionRefIsNotFound() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(actionRef = "nope.nothing")), RemoteErrorCategory.NOT_FOUND)
    }

    @Test
    fun remoteAllowedFalseOpsAreRejectedRemotely() {
        val d = Fx.dispatcher()
        Fx.assertError(remote(d, Fx.blob(operation = FleetProtocol.OP_CONFIG_EXPORT, actionRef = null)), RemoteErrorCategory.CAPABILITY_OFF)
        Fx.assertError(remote(d, Fx.blob(operation = FleetProtocol.OP_CONFIG_IMPORT, actionRef = null) {
            it.add("params", Fx.json("""{"dryRun":"true","bundle":"{}"}"""))
        }), RemoteErrorCategory.CAPABILITY_OFF)
        // Also not reachable by smuggling it through a macro.
        Fx.assertError(remote(d, Fx.blob(operation = FleetProtocol.OP_MACRO_RUN, actionRef = null) {
            it.add("steps", JsonParser.parseString("""[{"op":"config.export"}]"""))
        }), RemoteErrorCategory.CAPABILITY_OFF)
        Fx.assertError(remote(d, Fx.blob(operation = FleetProtocol.OP_MACRO_STEP, actionRef = null) { it.addProperty("op", "config.export") }),
            RemoteErrorCategory.CAPABILITY_OFF)
    }

    @Test
    fun remoteActionWithRemoteAllowedFalseIsCapabilityOff() {
        val d = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig() }, ReplayGuard(MutableClock()), MutableClock(),
            { Fx.snapshot(it) }, RemoteActionRegistry(listOf(RemoteAction(OpDescriptor("local.only", OpKind.READ, false, false)) { com.google.gson.JsonObject() })))
        Fx.assertError(d.handleRemote(Fx.DCA_PKG, null, Fx.blob(actionRef = "local.only")), RemoteErrorCategory.CAPABILITY_OFF)
    }

    @Test
    fun unsupportedOp() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(operation = "device.wipe")), RemoteErrorCategory.UNSUPPORTED_OP)
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(operation = FleetProtocol.OP_MACRO_STEP, actionRef = null) { it.addProperty("op", "not.advertised") }),
            RemoteErrorCategory.UNSUPPORTED_OP)
    }

    @Test
    fun standaloneOpIsNotAnActionRef() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(operation = FleetProtocol.OP_REMOTE_COMMAND, actionRef = "node.status.get")), RemoteErrorCategory.INVALID_PARAMETERS)
    }

    // --- macros ---

    @Test
    fun macroStepRunsAdvertisedOp() {
        val r = remote(Fx.dispatcher(), Fx.blob(operation = FleetProtocol.OP_MACRO_STEP, actionRef = null) {
            it.addProperty("op", "node.status.get"); it.addProperty("macroId", "m"); it.addProperty("stepIndex", 0)
        })
        assertTrue(r.toJson(), r.ok)
        assertEquals(Fx.NODE_ID, r.result!!.getAsJsonObject("output").get("nodeId").asString)
        assertEquals("m", r.result!!.get("macroId").asString)
    }

    @Test
    fun macroRunWithDestructiveStepNeedsConfirmationAndRunsNothing() {
        var ran = 0
        val reg = RemoteActionRegistry(RemoteActionRegistry.DEFAULT_ACTIONS + RemoteAction(OpDescriptor("count", OpKind.ACTION, true, false)) { ran++; com.google.gson.JsonObject() })
        val d = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig() }, ReplayGuard(MutableClock()), MutableClock(), { Fx.snapshot(it) }, reg)
        Fx.assertError(d.handleRemote(Fx.DCA_PKG, null, Fx.blob(operation = FleetProtocol.OP_MACRO_RUN, actionRef = null) {
            it.add("steps", JsonParser.parseString("""[{"op":"count"},{"op":"shell.exec"}]"""))
        }), RemoteErrorCategory.CONFIRMATION_REQUIRED)
        assertEquals(0, ran)
    }

    @Test
    fun macroRunDryRunValidatesWithoutExecuting() {
        var ran = 0
        val reg = RemoteActionRegistry(RemoteActionRegistry.DEFAULT_ACTIONS + RemoteAction(OpDescriptor("count", OpKind.ACTION, true, false)) { ran++; com.google.gson.JsonObject() })
        val d = RemoteEnvelopeDispatcher(Fx.NODE_ID, Fx.NODE_PKG, { Fx.pairedConfig() }, ReplayGuard(MutableClock()), MutableClock(), { Fx.snapshot(it) }, reg)
        val r = d.handleRemote(Fx.DCA_PKG, null, Fx.blob(operation = FleetProtocol.OP_MACRO_RUN, actionRef = null) {
            it.addProperty("dryRun", true)
            it.add("steps", JsonParser.parseString("""[{"op":"count"},{"op":"shell.exec"},{"op":"bogus"}]"""))
        })
        assertTrue(r.toJson(), r.ok)
        assertEquals(0, ran)
        val steps = r.result!!.getAsJsonArray("steps").map { it.asJsonObject }
        assertTrue(steps[0].get("ok").asBoolean)
        assertFalse(steps[0].get("requiresConfirm").asBoolean)
        assertTrue(steps[1].get("requiresConfirm").asBoolean)
        assertEquals("unsupported_op", steps[2].getAsJsonObject("error").get("category").asString)
    }

    @Test
    fun macroRunAbortStopsAndReportsPartial() {
        val r = remote(Fx.dispatcher(), Fx.blob(operation = FleetProtocol.OP_MACRO_RUN, actionRef = null) {
            it.add("steps", JsonParser.parseString(
                """[{"op":"node.ping"},{"op":"node.status.get","params":{"nodeId":"someone-else"}},{"op":"node.ping"}]"""))
        })
        assertTrue(r.toJson(), r.ok)
        assertTrue(r.result!!.get("aborted").asBoolean)
        assertFalse(r.result!!.get("completed").asBoolean)
        val steps = r.result!!.getAsJsonArray("steps")
        assertEquals(2, steps.size())
        assertEquals("not_found", steps[1].asJsonObject.getAsJsonObject("error").get("category").asString)
    }

    @Test
    fun macroRunContinueKeepsGoing() {
        val r = remote(Fx.dispatcher(), Fx.blob(operation = FleetProtocol.OP_MACRO_RUN, actionRef = null) {
            it.add("steps", JsonParser.parseString(
                """[{"op":"node.status.get","params":{"nodeId":"x"},"onError":"continue"},{"op":"node.ping"}]"""))
        })
        assertTrue(r.toJson(), r.ok)
        assertTrue(r.result!!.get("completed").asBoolean)
        assertEquals(2, r.result!!.getAsJsonArray("steps").size())
        assertTrue(r.result!!.getAsJsonArray("steps")[1].asJsonObject.get("ok").asBoolean)
    }

    @Test
    fun macrosCannotNest() {
        Fx.assertError(remote(Fx.dispatcher(), Fx.blob(operation = FleetProtocol.OP_MACRO_RUN, actionRef = null) {
            it.add("steps", JsonParser.parseString("""[{"op":"macro.run"}]"""))
        }), RemoteErrorCategory.INVALID_PARAMETERS)
    }

    @Test
    fun localDestructiveWithoutConfirmIsGated() {
        val e = FleetEnvelopeCodec.parse(Fx.blob(actionRef = "shell.exec"))
        Fx.assertError(Fx.dispatcher().handleLocal(e, confirmed = false), RemoteErrorCategory.CONFIRMATION_REQUIRED)
    }
}
