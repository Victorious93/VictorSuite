package com.termux.victor.remotecontrol

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One `remote.command` actionRef this node knows. */
data class RemoteAction(
    val descriptor: OpDescriptor,
    /** Runs the action. Only ever called for non-destructive, non-confirm actions. */
    val run: (params: Map<String, String>) -> JsonObject,
)

/**
 * `remote.command` actionRefs on this node. v1 ships:
 *  - `node.ping`   read, remote-allowed, returns `{ "pong": true }`
 *  - `shell.exec`  destructive, always `confirmation_required` (no on-device confirm flow yet,
 *                  so it never executes from a remote trigger)
 */
class RemoteActionRegistry(actions: List<RemoteAction> = DEFAULT_ACTIONS) {
    private val byName = actions.associateBy { it.descriptor.name }

    fun find(actionRef: String): RemoteAction? = byName[actionRef]

    fun descriptors(): List<OpDescriptor> = byName.values.map {
        val kind = ActionClassifier.effectiveKind(it.descriptor.name, it.descriptor.kind)
        it.descriptor.copy(kind = kind, requiresConfirm = it.descriptor.requiresConfirm || kind == OpKind.DESTRUCTIVE)
    }

    companion object {
        val DEFAULT_ACTIONS = listOf(
            RemoteAction(OpDescriptor("node.ping", OpKind.READ, remoteAllowed = true, requiresConfirm = false)) {
                JsonObject().apply { addProperty("pong", true) }
            },
            RemoteAction(OpDescriptor("shell.exec", OpKind.DESTRUCTIVE, remoteAllowed = true, requiresConfirm = true)) {
                throw RemoteControlException(RemoteErrorCategory.CONFIRMATION_REQUIRED, "shell.exec needs on-device confirmation")
            },
        )
    }
}

/** Frozen BridgeResponse shape: `{ protocol_major, request_id, node_id, ok, result | error{category,message} }`. */
data class RemoteResponse(
    val requestId: String?,
    val nodeId: String,
    val ok: Boolean,
    val result: JsonObject? = null,
    val errorCategory: RemoteErrorCategory? = null,
    val errorMessage: String? = null,
) {
    fun toJson(): String = JsonObject().apply {
        addProperty("protocol_major", FleetProtocol.PROTOCOL_MAJOR)
        if (requestId != null) addProperty("request_id", requestId) else add("request_id", JsonNull.INSTANCE)
        addProperty("node_id", nodeId)
        addProperty("ok", ok)
        if (ok) add("result", result ?: JsonObject())
        else add("error", JsonObject().apply {
            addProperty("category", (errorCategory ?: RemoteErrorCategory.INTERNAL).wire)
            addProperty("message", errorMessage ?: "")
        })
    }.toString()
}

/**
 * Trust pipeline for an inbound envelope (REMOTE_CONTROL.md "TRUST MODEL"), in order:
 *  1. caller-id: authenticated calling package -> pairing row -> sourceNodeRef
 *  2. external-control switch (OFF by default)
 *  3. envelope parse + replay/expiry/skew
 *  4. this node's own policy (targetNodeRef, op descriptor, independent classification,
 *     destructive -> confirmation_required; a remote trigger NEVER auto-approves)
 *
 * Pure JVM. The Android binding only supplies the authenticated caller and the raw blob.
 */
class RemoteEnvelopeDispatcher(
    private val nodeId: String,
    private val nodePackage: String,
    private val config: () -> RemoteControlConfig,
    private val replayGuard: ReplayGuard,
    private val clock: RemoteClock = RemoteClock.SYSTEM,
    private val statusProvider: (nowMs: Long) -> NodeStatusSnapshot,
    private val actions: RemoteActionRegistry = RemoteActionRegistry(),
    private val configSource: NodeConfigSource? = null,
    private val configSink: NodeConfigSink? = null,
) {

    /** Remote entry point (RUN_COMMAND binding). Caller identity is transport-derived only. */
    fun handleRemote(callingPackage: String?, callerCertSha256: String?, rawEnvelope: String?): RemoteResponse {
        var requestId: String? = bestEffortRequestId(rawEnvelope)
        return try {
            val cfg = config()
            val identity = cfg.pairingTable().authenticate(callingPackage, callerCertSha256)
            if (!cfg.externalControlEnabled) {
                throw RemoteControlException(RemoteErrorCategory.EXTERNAL_CONTROL_DISABLED, "external control is off on this node")
            }
            val env = FleetEnvelopeCodec.parse(rawEnvelope)
            requestId = env.requestId
            replayGuard.checkAndRecord(identity.sourceNodeRef, env)
            requireTarget(env.targetNodeRef)
            val result = dispatch(env.operation, env, remote = true, confirmed = false, aead = null)
            RemoteResponse(requestId, nodeId, ok = true, result = result)
        } catch (e: RemoteControlException) {
            RemoteResponse(requestId, nodeId, ok = false, errorCategory = e.category, errorMessage = e.message)
        } catch (e: Exception) {
            RemoteResponse(requestId, nodeId, ok = false, errorCategory = RemoteErrorCategory.INTERNAL, errorMessage = "internal error")
        }
    }

    /**
     * Local (on-device UI) entry point. Skips pairing / external-control / replay
     * because the user is driving it in-process. [confirmed] is the on-device
     * confirmation and is the ONLY way `config.import` or a destructive op runs.
     */
    fun handleLocal(env: FleetEnvelope, confirmed: Boolean, aead: FleetBackupAead? = null): RemoteResponse = try {
        RemoteResponse(env.requestId, nodeId, ok = true, result = dispatch(env.operation, env, remote = false, confirmed = confirmed, aead = aead))
    } catch (e: RemoteControlException) {
        RemoteResponse(env.requestId, nodeId, ok = false, errorCategory = e.category, errorMessage = e.message)
    } catch (e: Exception) {
        RemoteResponse(env.requestId, nodeId, ok = false, errorCategory = RemoteErrorCategory.INTERNAL, errorMessage = "internal error")
    }

    private fun requireTarget(target: String) {
        if (target != nodeId && target != nodePackage) {
            throw RemoteControlException(RemoteErrorCategory.NOT_FOUND, "targetNodeRef '$target' is not this node")
        }
    }

    private fun dispatch(op: String, env: FleetEnvelope, remote: Boolean, confirmed: Boolean, aead: FleetBackupAead?): JsonObject {
        val d = NodeCapabilities.descriptor(op)
            ?: throw RemoteControlException(RemoteErrorCategory.UNSUPPORTED_OP, "unsupported op '$op'")
        if (remote && !d.remoteAllowed) {
            throw RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "'$op' is not remote-allowed on this node")
        }
        return when (op) {
            FleetProtocol.OP_CAPABILITIES_GET -> capabilities()
            FleetProtocol.OP_NODE_STATUS_GET -> nodeStatus(env.params)
            FleetProtocol.OP_REMOTE_COMMAND -> remoteCommand(env.actionRef, env.params, remote, confirmed)
            FleetProtocol.OP_MACRO_STEP -> macroStep(env, remote, confirmed)
            FleetProtocol.OP_MACRO_RUN -> macroRun(env, remote, confirmed)
            FleetProtocol.OP_CONFIG_EXPORT -> configExport(env.params, aead)
            FleetProtocol.OP_CONFIG_IMPORT -> configImport(env, confirmed, aead)
            else -> throw RemoteControlException(RemoteErrorCategory.UNSUPPORTED_OP, "unsupported op '$op'")
        }
    }

    private fun capabilities(): JsonObject {
        val o = JsonParser.parseString(NodeCapabilities.capabilitiesJson()).asJsonObject
        o.addProperty("nodeId", nodeId)
        o.add("actions", JsonArray().also { arr -> actions.descriptors().forEach { arr.add(it.toJson()) } })
        return o
    }

    private fun nodeStatus(params: Map<String, String>): JsonObject {
        // Unknown args are ignored. args.nodeId, when present, must be this node.
        params["nodeId"]?.takeIf { it.isNotBlank() }?.let {
            if (it != nodeId) throw RemoteControlException(RemoteErrorCategory.NOT_FOUND, "nodeId '$it' is not this node")
        }
        val snapshot = statusProvider(clock.nowMs())
        return JsonParser.parseString(NodeStatusSnapshotCodec.encode(snapshot)).asJsonObject
    }

    /** Resolve the effective descriptor for a `remote.command` actionRef (classification can only raise). */
    private fun actionDescriptor(actionRef: String): Pair<RemoteAction, OpDescriptor> {
        val action = actions.find(actionRef)
            ?: throw RemoteControlException(RemoteErrorCategory.NOT_FOUND, "unknown actionRef '$actionRef'")
        val kind = ActionClassifier.effectiveKind(actionRef, action.descriptor.kind)
        val confirm = action.descriptor.requiresConfirm || kind == OpKind.DESTRUCTIVE
        return action to action.descriptor.copy(kind = kind, requiresConfirm = confirm)
    }

    private fun remoteCommand(actionRef: String, params: Map<String, String>, remote: Boolean, confirmed: Boolean): JsonObject {
        if (actionRef in FleetProtocol.KNOWN_OPS) {
            invalid("'$actionRef' is a standalone operation, not a remote.command actionRef")
        }
        val (action, d) = actionDescriptor(actionRef)
        if (remote && !d.remoteAllowed) throw RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "'$actionRef' is not remote-allowed")
        // Advisory envelope `destructive` and any params (e.g. "confirmed":"true") are ignored here.
        // A remote trigger never carries an on-device confirmation.
        if (d.requiresConfirm && (remote || !confirmed)) {
            throw RemoteControlException(RemoteErrorCategory.CONFIRMATION_REQUIRED, "'$actionRef' requires on-device confirmation")
        }
        if (d.kind == OpKind.DESTRUCTIVE) {
            // No local destructive executor in v1 either.
            throw RemoteControlException(RemoteErrorCategory.CONFIRMATION_REQUIRED, "'$actionRef' is destructive; no executor in v1")
        }
        return JsonObject().apply {
            addProperty("actionRef", actionRef)
            add("output", action.run(params))
        }
    }

    /** Descriptor for a macro step op: an advertised non-macro op, or a registered actionRef. */
    private fun stepDescriptor(op: String): OpDescriptor {
        if (op == FleetProtocol.OP_MACRO_RUN || op == FleetProtocol.OP_MACRO_STEP) invalid("macros cannot nest '$op'")
        NodeCapabilities.descriptor(op)?.let { return it }
        actions.find(op)?.let { return actionDescriptor(op).second }
        throw RemoteControlException(RemoteErrorCategory.UNSUPPORTED_OP, "step op '$op' is not advertised on this node")
    }

    private fun runStep(op: String, params: Map<String, String>, parent: FleetEnvelope, remote: Boolean, confirmed: Boolean): JsonObject {
        val d = stepDescriptor(op)
        if (remote && !d.remoteAllowed) throw RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "'$op' is not remote-allowed")
        return if (NodeCapabilities.descriptor(op) != null && op != FleetProtocol.OP_REMOTE_COMMAND) {
            dispatch(op, parent.copy(operation = op, actionRef = op, params = params, steps = null, dryRun = false), remote, confirmed, null)
        } else {
            val actionRef = if (op == FleetProtocol.OP_REMOTE_COMMAND) params["actionRef"] ?: invalid("remote.command step needs params.actionRef") else op
            remoteCommand(actionRef, params - "actionRef", remote, confirmed)
        }
    }

    private fun macroStep(env: FleetEnvelope, remote: Boolean, confirmed: Boolean): JsonObject {
        val op = env.stepOp ?: invalid("macro.step needs 'op'")
        val out = runStep(op, env.params, env, remote, confirmed)
        return JsonObject().apply {
            env.macroId?.let { addProperty("macroId", it) }
            env.stepIndex?.let { addProperty("stepIndex", it) }
            addProperty("op", op)
            addProperty("onError", env.onError ?: FleetProtocol.ON_ERROR_ABORT)
            add("output", out)
        }
    }

    private fun stepEffectiveConfirm(step: MacroStepSpec): Boolean {
        val d = stepDescriptor(step.op)
        if (step.op == FleetProtocol.OP_REMOTE_COMMAND) {
            val ref = step.params["actionRef"] ?: return false
            return actionDescriptor(ref).second.requiresConfirm
        }
        return d.requiresConfirm || d.kind == OpKind.DESTRUCTIVE
    }

    /**
     * Node macro runner: only this node's advertised ops, sequential, per-step
     * results, `abort` = stop on first error and report partial, no rollback.
     * Any step that needs confirmation gates the whole (non-dry-run) macro.
     */
    private fun macroRun(env: FleetEnvelope, remote: Boolean, confirmed: Boolean): JsonObject {
        val steps = env.steps ?: invalid("macro.run needs 'steps'")
        val results = JsonArray()
        if (env.dryRun) {
            steps.forEachIndexed { i, s ->
                val r = JsonObject().apply { addProperty("index", i); addProperty("op", s.op) }
                try {
                    val d = stepDescriptor(s.op)
                    if (remote && !d.remoteAllowed) throw RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "'${s.op}' is not remote-allowed")
                    r.addProperty("ok", true)
                    r.addProperty("requiresConfirm", stepEffectiveConfirm(s))
                } catch (e: RemoteControlException) {
                    r.addProperty("ok", false)
                    r.add("error", errorJson(e))
                }
                results.add(r)
            }
            return macroResult(env, dryRun = true, completed = false, aborted = false, results)
        }
        // Pre-flight: unknown / not-remote-allowed steps fail fast; any confirm-gated step gates the macro.
        steps.forEach { s ->
            val d = stepDescriptor(s.op)
            if (remote && !d.remoteAllowed) throw RemoteControlException(RemoteErrorCategory.CAPABILITY_OFF, "'${s.op}' is not remote-allowed")
        }
        if (steps.any { stepEffectiveConfirm(it) } && (remote || !confirmed)) {
            throw RemoteControlException(RemoteErrorCategory.CONFIRMATION_REQUIRED, "macro contains a step that requires on-device confirmation")
        }
        var aborted = false
        for ((i, s) in steps.withIndex()) {
            val r = JsonObject().apply { addProperty("index", i); addProperty("op", s.op) }
            try {
                r.add("output", runStep(s.op, s.params, env, remote, confirmed))
                r.addProperty("ok", true)
            } catch (e: RemoteControlException) {
                r.addProperty("ok", false)
                r.add("error", errorJson(e))
                results.add(r)
                if (s.onError == FleetProtocol.ON_ERROR_ABORT) { aborted = true; break }
                continue
            }
            results.add(r)
        }
        return macroResult(env, dryRun = false, completed = !aborted, aborted = aborted, results)
    }

    private fun macroResult(env: FleetEnvelope, dryRun: Boolean, completed: Boolean, aborted: Boolean, steps: JsonArray) = JsonObject().apply {
        env.macroId?.let { addProperty("macroId", it) }
        addProperty("dryRun", dryRun)
        addProperty("completed", completed)
        addProperty("aborted", aborted)
        add("steps", steps)
    }

    private fun errorJson(e: RemoteControlException) = JsonObject().apply {
        addProperty("category", e.category.wire)
        addProperty("message", e.message ?: "")
    }

    private fun configExport(params: Map<String, String>, aead: FleetBackupAead?): JsonObject {
        val source = configSource ?: throw RemoteControlException(RemoteErrorCategory.UNSUPPORTED_OP, "no config surface")
        params["packageName"]?.takeIf { it.isNotBlank() }?.let {
            if (it != nodePackage) throw RemoteControlException(RemoteErrorCategory.NOT_FOUND, "no config for '$it'")
        }
        val clear = params["clearPayload"].equals("true", ignoreCase = true)
        val bundle = NodeConfigBackup.export(source, nodeId, clock.nowMs(), aead, clearPayload = clear)
        return JsonObject().apply { add("bundle", JsonParser.parseString(bundle)) }
    }

    private fun configImport(env: FleetEnvelope, confirmed: Boolean, aead: FleetBackupAead?): JsonObject {
        val source = configSource ?: throw RemoteControlException(RemoteErrorCategory.UNSUPPORTED_OP, "no config surface")
        val policy = BackupConflictPolicy.fromWire(env.params["conflictPolicy"]) ?: invalid("unknown conflictPolicy")
        val report = NodeConfigBackup.import(env.params["bundle"], source, configSink, aead, policy, env.dryRun, confirmed)
        return report.toJson()
    }

    private fun bestEffortRequestId(raw: String?): String? {
        if (raw == null || raw.length > FleetProtocol.MAX_BLOB_CHARS) return null
        return try {
            val el: JsonElement = JsonParser.parseString(raw)
            el.asJsonObject.get("requestId")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.take(FleetProtocol.MAX_ID_CHARS)
        } catch (e: Exception) {
            null
        }
    }
}
