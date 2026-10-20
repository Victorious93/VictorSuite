package com.termux.zerocore.remotecontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Op classification. `destructive` ops are always confirmation-gated and never remote-allowed. */
enum class OpKind(val wire: String) {
    READ("read"),
    ACTION("action"),
    DESTRUCTIVE("destructive"),
}

/** Per-op descriptor advertised by `capabilities.get` (REMOTE_CONTROL.md §1). */
data class OpDescriptor(
    val name: String,
    val kind: OpKind,
    val remoteAllowed: Boolean,
    val requiresConfirm: Boolean,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("name", name)
        addProperty("kind", kind.wire)
        addProperty("remoteAllowed", remoteAllowed)
        addProperty("requiresConfirm", requiresConfirm)
    }
}

/**
 * The ops this VictorSuite node advertises. Frozen defaults:
 *  - `node.status.get`  read, remoteAllowed=true,  requiresConfirm=false  (STATUS_TRAY.md §1)
 *  - `config.export`    action, remoteAllowed=false, requiresConfirm=false (UNIFIED_BACKUP.md §1)
 *  - `config.import`    action, remoteAllowed=false, requiresConfirm=true  (UNIFIED_BACKUP.md §1)
 *  - `remote.command`   action; its destructive-ness comes from the actionRef classifier
 *  - shell/exec actionRefs are destructive by default.
 */
object NodeCapabilities {
    val DESCRIPTORS: List<OpDescriptor> = listOf(
        OpDescriptor(FleetProtocol.OP_CAPABILITIES_GET, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
        OpDescriptor(FleetProtocol.OP_NODE_STATUS_GET, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
        OpDescriptor(FleetProtocol.OP_REMOTE_COMMAND, OpKind.ACTION, remoteAllowed = true, requiresConfirm = false),
        OpDescriptor(FleetProtocol.OP_MACRO_STEP, OpKind.ACTION, remoteAllowed = true, requiresConfirm = false),
        OpDescriptor(FleetProtocol.OP_MACRO_RUN, OpKind.ACTION, remoteAllowed = true, requiresConfirm = false),
        OpDescriptor(FleetProtocol.OP_CONFIG_EXPORT, OpKind.ACTION, remoteAllowed = false, requiresConfirm = false),
        OpDescriptor(FleetProtocol.OP_CONFIG_IMPORT, OpKind.ACTION, remoteAllowed = false, requiresConfirm = true),
    )

    private val byName = DESCRIPTORS.associateBy { it.name }

    fun descriptor(op: String): OpDescriptor? = byName[op]

    fun capabilitiesJson(): String {
        val ops = JsonArray()
        DESCRIPTORS.forEach { ops.add(it.toJson()) }
        return JsonObject().apply {
            addProperty("protocolMajor", FleetProtocol.PROTOCOL_MAJOR)
            addProperty("protocolMinor", FleetProtocol.PROTOCOL_MINOR)
            add("ops", ops)
        }.toString()
    }
}

/**
 * Independent classification of an `actionRef` (REMOTE_CONTROL.md §4). The
 * advisory `destructive` flag on the envelope is NEVER trusted to lower this.
 * Shell / exec actions are destructive by default. A forged `destructive:false`
 * on a wipe/power-off/flash/bootloader action is still classified destructive.
 */
object ActionClassifier {
    /** Whole-word tokens (split on non-alphanumerics) that force `destructive`. */
    private val DESTRUCTIVE_TOKENS = setOf(
        "wipe", "poweroff", "shutdown", "reboot", "flash", "bootloader", "format",
        "erase", "lock", "shell", "exec", "sh", "bash", "su", "root", "sudo",
        "kill", "uninstall", "delete", "rm",
    )

    /** Substrings that force `destructive` regardless of tokenisation. */
    private val DESTRUCTIVE_SUBSTRINGS = listOf("power_off", "power-off", "factory_reset", "factory-reset", "run_command", "runcommand")

    fun classify(actionRef: String): OpKind {
        val folded = actionRef.lowercase()
        if (DESTRUCTIVE_SUBSTRINGS.any { folded.contains(it) }) return OpKind.DESTRUCTIVE
        val tokens = folded.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (tokens.any { it in DESTRUCTIVE_TOKENS }) return OpKind.DESTRUCTIVE
        return OpKind.ACTION
    }

    /** Effective kind: the registry's kind, raised (never lowered) by [classify]. */
    fun effectiveKind(actionRef: String, registered: OpKind): OpKind {
        val classified = classify(actionRef)
        return if (classified == OpKind.DESTRUCTIVE || registered == OpKind.DESTRUCTIVE) OpKind.DESTRUCTIVE else registered
    }
}
