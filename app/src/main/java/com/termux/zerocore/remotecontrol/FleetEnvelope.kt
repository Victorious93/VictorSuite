package com.termux.zerocore.remotecontrol

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** Frozen op names and protocol constants (BRIDGE_PROTOCOL major 1, minor 1). */
object FleetProtocol {
    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 1

    const val OP_CAPABILITIES_GET = "capabilities.get"
    const val OP_REMOTE_COMMAND = "remote.command"
    const val OP_MACRO_STEP = "macro.step"
    const val OP_MACRO_RUN = "macro.run"
    const val OP_NODE_STATUS_GET = "node.status.get"
    const val OP_CONFIG_EXPORT = "config.export"
    const val OP_CONFIG_IMPORT = "config.import"

    val KNOWN_OPS = setOf(
        OP_CAPABILITIES_GET, OP_REMOTE_COMMAND, OP_MACRO_STEP, OP_MACRO_RUN,
        OP_NODE_STATUS_GET, OP_CONFIG_EXPORT, OP_CONFIG_IMPORT,
    )

    /** Ops that are standalone BridgeRequest operations (not `actionRef`s under remote.command). */
    val STANDALONE_OPS = setOf(OP_CAPABILITIES_GET, OP_NODE_STATUS_GET, OP_CONFIG_EXPORT, OP_CONFIG_IMPORT)

    const val DEFAULT_TTL_MS = 120_000L
    const val MAX_TTL_MS = 300_000L

    /** Future-skew allowance. Past validity is TTL-only (REMOTE_CONTROL.md §2). */
    const val FUTURE_SKEW_MS = 120_000L

    /** Seen-nonce retention floor (>= 10 minutes, >= max ttl). */
    const val REPLAY_RETENTION_MS = 10 * 60 * 1000L

    const val ON_ERROR_ABORT = "abort"
    const val ON_ERROR_CONTINUE = "continue"

    const val MAX_PARAMS = 16
    const val MAX_STEPS = 32
    const val MAX_ID_CHARS = 128
    const val MAX_BLOB_CHARS = 256 * 1024

    /**
     * TERMUX_IPC binding: the single JSON-blob extra on `com.victorious.suite.RUN_COMMAND`.
     * Key is the controller proposal from REMOTE_CONTROL.md Appendix C / §7 item 4.
     */
    const val TERMUX_ENVELOPE_EXTRA = "ai.droidcommand.REMOTE_ENVELOPE_JSON"

    /** Response JSON extra put on the caller's result PendingIntent (VictorSuite proposal). */
    const val TERMUX_RESPONSE_EXTRA = "ai.droidcommand.REMOTE_RESPONSE_JSON"

    private val HEX_128 = Regex("^[0-9a-fA-F]{32}$")

    /** 128-bit nonce: 32 hex chars, or base64 / base64url that decodes to exactly 16 bytes. */
    fun isValidNonce(value: String): Boolean {
        if (HEX_128.matches(value)) return true
        if (value.length !in 22..24) return false
        val padded = when (value.length % 4) {
            0 -> value
            2 -> "$value=="
            3 -> "$value="
            else -> return false
        }
        val decoded = runCatching { java.util.Base64.getDecoder().decode(padded) }.getOrNull()
            ?: runCatching { java.util.Base64.getUrlDecoder().decode(padded) }.getOrNull()
        return decoded != null && decoded.size == 16
    }
}

data class MacroStepSpec(
    val op: String,
    val params: Map<String, String> = emptyMap(),
    val onError: String = FleetProtocol.ON_ERROR_ABORT,
    val timeoutMs: Long? = null,
)

/**
 * The frozen v1 envelope (camelCase), as carried in the TERMUX_IPC JSON blob.
 *
 * Nothing in here is identity. [destructive] is advisory only and [triggerJson]
 * is telemetry only; neither is ever used for gating. The authenticated
 * `sourceNodeRef` comes from the Android binding, not from this object.
 */
data class FleetEnvelope(
    val operation: String,
    val requestId: String,
    val targetNodeRef: String,
    val actionRef: String,
    val params: Map<String, String>,
    val nonce: String,
    val timestampMs: Long,
    val ttlMs: Long,
    val destructive: Boolean,
    val triggerJson: String?,
    // macro.step
    val macroId: String? = null,
    val stepIndex: Int? = null,
    val onError: String? = null,
    val stepOp: String? = null,
    // macro.run
    val steps: List<MacroStepSpec>? = null,
    val dryRun: Boolean = false,
)

/**
 * Strict parser for the TERMUX_IPC JSON blob. Every failure is
 * `invalid_parameters` (or `unsupported_op` for an unknown explicit operation).
 * Unknown top-level keys are ignored (forward compatible).
 */
object FleetEnvelopeCodec {

    fun parse(raw: String?): FleetEnvelope {
        if (raw.isNullOrBlank()) invalid("envelope JSON is missing")
        if (raw.length > FleetProtocol.MAX_BLOB_CHARS) invalid("envelope exceeds ${FleetProtocol.MAX_BLOB_CHARS} chars")
        val root = try {
            JsonParser.parseString(raw)
        } catch (e: RuntimeException) {
            invalid("envelope is not valid JSON")
        }
        if (!root.isJsonObject) invalid("envelope must be a JSON object")
        return parse(root.asJsonObject)
    }

    fun parse(obj: JsonObject): FleetEnvelope {
        if (obj.has("auth")) invalid("nested 'auth' is superseded; anti-replay fields are top-level")

        val requestId = requiredId(obj, "requestId")
        val targetNodeRef = requiredId(obj, "targetNodeRef")
        val nonce = requiredString(obj, "nonce")
        if (!FleetProtocol.isValidNonce(nonce)) invalid("nonce must be 128-bit (32 hex chars or base64 of 16 bytes)")
        val timestampMs = requiredLong(obj, "timestampMs")
        if (timestampMs <= 0) invalid("timestampMs must be positive epoch ms")
        val ttlMs = optionalLong(obj, "ttlMs") ?: FleetProtocol.DEFAULT_TTL_MS
        if (ttlMs <= 0) invalid("ttlMs must be positive")
        if (ttlMs > FleetProtocol.MAX_TTL_MS) invalid("ttlMs exceeds max ${FleetProtocol.MAX_TTL_MS}")
        val destructive = optionalBoolean(obj, "destructive") ?: false
        val trigger = obj.get("trigger")?.takeUnless { it.isJsonNull }?.let {
            if (!it.isJsonObject) invalid("trigger must be an object")
            it.toString()
        }

        // `params` is canonical for this node; `args` is the DroidCommand AI wire name.
        val params = paramsOf(obj, "params")
        val args = paramsOf(obj, "args")
        if (params.isNotEmpty() && args.isNotEmpty() && params != args) {
            invalid("send either 'params' or 'args', not both")
        }
        val mergedParams = params.ifEmpty { args }

        val actionRefRaw = optionalString(obj, "actionRef")?.trim()?.takeIf { it.isNotEmpty() }
        val stepOp = optionalString(obj, "op")?.trim()?.takeIf { it.isNotEmpty() }
        val stepsJson = obj.get("steps")?.takeUnless { it.isJsonNull }

        val explicitOperation = optionalString(obj, "operation")?.trim()?.takeIf { it.isNotEmpty() }
        val operation = when {
            explicitOperation != null -> {
                if (explicitOperation !in FleetProtocol.KNOWN_OPS) {
                    throw RemoteControlException(RemoteErrorCategory.UNSUPPORTED_OP, "unsupported operation '$explicitOperation'")
                }
                explicitOperation
            }
            // Inference for blobs without `operation` (DroidCommand AI's TermuxIpc blob has none).
            stepsJson != null -> FleetProtocol.OP_MACRO_RUN
            actionRefRaw != null && actionRefRaw in FleetProtocol.STANDALONE_OPS -> actionRefRaw
            stepOp != null -> FleetProtocol.OP_MACRO_STEP
            else -> FleetProtocol.OP_REMOTE_COMMAND
        }

        if (operation == FleetProtocol.OP_REMOTE_COMMAND && actionRefRaw == null) invalid("actionRef is required for remote.command")
        val actionRef = actionRefRaw ?: operation
        if (actionRef.length > FleetProtocol.MAX_ID_CHARS) invalid("actionRef too long")

        var macroId: String? = null
        var stepIndex: Int? = null
        var onError: String? = null
        var steps: List<MacroStepSpec>? = null
        var dryRun = optionalBoolean(obj, "dryRun") ?: false

        when (operation) {
            FleetProtocol.OP_MACRO_STEP -> {
                macroId = optionalId(obj, "macroId")
                stepIndex = optionalLong(obj, "stepIndex")?.let {
                    if (it < 0 || it > Int.MAX_VALUE) invalid("stepIndex must be >= 0")
                    it.toInt()
                }
                onError = onErrorOf(optionalString(obj, "onError"))
                if (stepOp == null && actionRefRaw == null) invalid("macro.step needs 'op'")
            }
            FleetProtocol.OP_MACRO_RUN -> {
                macroId = optionalId(obj, "macroId")
                steps = stepsOf(stepsJson ?: invalid("macro.run needs 'steps'"))
            }
            FleetProtocol.OP_CONFIG_IMPORT -> {
                // dryRun may sit at the top level or in params (UNIFIED_BACKUP.md §1).
                dryRun = dryRun || mergedParams["dryRun"].equals("true", ignoreCase = true)
            }
        }

        return FleetEnvelope(
            operation = operation,
            requestId = requestId,
            targetNodeRef = targetNodeRef,
            actionRef = actionRef,
            params = mergedParams,
            nonce = nonce,
            timestampMs = timestampMs,
            ttlMs = ttlMs,
            destructive = destructive,
            triggerJson = trigger,
            macroId = macroId,
            stepIndex = stepIndex,
            onError = onError,
            stepOp = if (operation == FleetProtocol.OP_MACRO_STEP) (stepOp ?: actionRefRaw) else null,
            steps = steps,
            dryRun = dryRun,
        )
    }

    /** Encode for tests / for callers building a blob. Always emits `operation` and `params`. */
    fun encode(e: FleetEnvelope): String {
        val o = JsonObject()
        o.addProperty("operation", e.operation)
        o.addProperty("requestId", e.requestId)
        o.addProperty("targetNodeRef", e.targetNodeRef)
        o.addProperty("actionRef", e.actionRef)
        o.add("params", JsonObject().also { p -> e.params.forEach { (k, v) -> p.addProperty(k, v) } })
        o.addProperty("nonce", e.nonce)
        o.addProperty("timestampMs", e.timestampMs)
        o.addProperty("ttlMs", e.ttlMs)
        o.addProperty("destructive", e.destructive)
        e.triggerJson?.let { o.add("trigger", JsonParser.parseString(it)) }
        e.macroId?.let { o.addProperty("macroId", it) }
        e.stepIndex?.let { o.addProperty("stepIndex", it) }
        e.onError?.let { o.addProperty("onError", it) }
        e.stepOp?.let { o.addProperty("op", it) }
        e.steps?.let { list ->
            val arr = JsonArray()
            list.forEach { s ->
                arr.add(JsonObject().also { so ->
                    so.addProperty("op", s.op)
                    so.add("params", JsonObject().also { p -> s.params.forEach { (k, v) -> p.addProperty(k, v) } })
                    so.addProperty("onError", s.onError)
                    s.timeoutMs?.let { so.addProperty("timeoutMs", it) }
                })
            }
            o.add("steps", arr)
        }
        if (e.dryRun) o.addProperty("dryRun", true)
        return o.toString()
    }

    private fun stepsOf(el: JsonElement): List<MacroStepSpec> {
        if (!el.isJsonArray) invalid("steps must be an array")
        val arr = el.asJsonArray
        if (arr.size() == 0) invalid("steps must not be empty")
        if (arr.size() > FleetProtocol.MAX_STEPS) invalid("steps exceeds ${FleetProtocol.MAX_STEPS}")
        return arr.mapIndexed { i, s ->
            if (!s.isJsonObject) invalid("steps[$i] must be an object")
            val so = s.asJsonObject
            val op = optionalString(so, "op")?.trim()?.takeIf { it.isNotEmpty() } ?: invalid("steps[$i].op is required")
            val timeout = optionalLong(so, "timeoutMs")?.also { if (it <= 0) invalid("steps[$i].timeoutMs must be positive") }
            MacroStepSpec(
                op = op,
                params = paramsOf(so, "params").ifEmpty { paramsOf(so, "args") },
                onError = onErrorOf(optionalString(so, "onError")),
                timeoutMs = timeout,
            )
        }
    }

    private fun onErrorOf(value: String?): String = when (value?.trim()) {
        null, "", FleetProtocol.ON_ERROR_ABORT -> FleetProtocol.ON_ERROR_ABORT
        FleetProtocol.ON_ERROR_CONTINUE -> FleetProtocol.ON_ERROR_CONTINUE
        else -> invalid("onError must be 'abort' or 'continue'")
    }

    private fun paramsOf(obj: JsonObject, key: String): Map<String, String> {
        val el = obj.get(key) ?: return emptyMap()
        if (el.isJsonNull) return emptyMap()
        if (!el.isJsonObject) invalid("$key must be an object")
        val out = LinkedHashMap<String, String>()
        for ((k, v) in el.asJsonObject.entrySet()) {
            if (v.isJsonNull) continue
            if (!v.isJsonPrimitive) invalid("$key.$k must be a string, number or boolean")
            out[k] = v.asJsonPrimitive.asString
        }
        if (out.size > FleetProtocol.MAX_PARAMS) invalid("$key exceeds ${FleetProtocol.MAX_PARAMS} entries")
        return out
    }

    private fun primitive(obj: JsonObject, key: String): JsonPrimitive? {
        val el = obj.get(key) ?: return null
        if (el.isJsonNull) return null
        if (!el.isJsonPrimitive) invalid("$key must be a primitive")
        return el.asJsonPrimitive
    }

    private fun optionalString(obj: JsonObject, key: String): String? {
        val p = primitive(obj, key) ?: return null
        if (!p.isString) invalid("$key must be a string")
        return p.asString
    }

    private fun requiredString(obj: JsonObject, key: String): String =
        optionalString(obj, key)?.takeIf { it.isNotBlank() } ?: invalid("$key is required")

    private fun requiredId(obj: JsonObject, key: String): String {
        val v = requiredString(obj, key).trim()
        if (v.length > FleetProtocol.MAX_ID_CHARS) invalid("$key too long")
        return v
    }

    private fun optionalId(obj: JsonObject, key: String): String? {
        val v = optionalString(obj, key)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (v.length > FleetProtocol.MAX_ID_CHARS) invalid("$key too long")
        return v
    }

    private fun optionalLong(obj: JsonObject, key: String): Long? {
        val p = primitive(obj, key) ?: return null
        if (!p.isNumber) invalid("$key must be a number")
        val d = p.asBigDecimal
        return try {
            d.longValueExact()
        } catch (e: ArithmeticException) {
            invalid("$key must be an integer")
        }
    }

    private fun requiredLong(obj: JsonObject, key: String): Long = optionalLong(obj, key) ?: invalid("$key is required")

    private fun optionalBoolean(obj: JsonObject, key: String): Boolean? {
        val p = primitive(obj, key) ?: return null
        return when {
            p.isBoolean -> p.asBoolean
            p.isString && p.asString.equals("true", true) -> true
            p.isString && p.asString.equals("false", true) -> false
            else -> invalid("$key must be a boolean")
        }
    }
}
