package com.termux.victor.pc

/** Why a mirror plan was refused. */
enum class MirrorRejection {
    BAD_HOST,
    NOT_TRUSTED_NETWORK,
    BAD_ADB_PORT,
    BAD_PAIRING_PORT,
}

sealed class MirrorPlan {
    /**
     * Commands to run on the PC. The PC is the scrcpy client; this phone is the device mirrored.
     * [pair] is null when no pairing port was given (pairing is only needed once per PC).
     */
    data class Ready(
        val host: String,
        val kind: AddrKind,
        val adbPort: Int,
        val pair: String?,
        val connect: String,
        val scrcpy: String,
        val disconnect: String,
    ) : MirrorPlan() {
        /** All commands as one pasteable block with short comments. */
        fun asScript(): String = buildString {
            appendLine("# Run these on the PC (Ubuntu), not on the phone.")
            if (pair != null) {
                appendLine("# 1. Pair once (enter the 6-digit code shown on the phone):")
                appendLine(pair)
            }
            appendLine("# ${if (pair != null) "2" else "1"}. Connect:")
            appendLine(connect)
            appendLine("# ${if (pair != null) "3" else "2"}. Mirror and control the phone:")
            appendLine(scrcpy)
            appendLine("# When finished:")
            append(disconnect)
        }
    }

    data class Rejected(val reason: MirrorRejection) : MirrorPlan()
}

/**
 * Builds the PC-side command lines for the "PC mirrors and controls this phone" flow.
 *
 * Refuses anything that is not a private-LAN or Tailscale IPv4 address, so the app never
 * suggests pointing ADB at a public address. Values are validated strictly (digits and dots only),
 * which also keeps them safe to paste into a shell.
 */
object PcMirrorCommands {

    /** Android's wireless-debugging ports are ephemeral; a port below 1024 is never right. */
    const val MIN_ADB_PORT = 1024

    fun plan(host: String, adbPort: Int?, pairingPort: Int? = null): MirrorPlan {
        val trimmed = host.trim()
        val kind = PcMirrorNet.classify(trimmed)
        if (kind == AddrKind.INVALID) return MirrorPlan.Rejected(MirrorRejection.BAD_HOST)
        if (!PcMirrorNet.isTrusted(kind)) return MirrorPlan.Rejected(MirrorRejection.NOT_TRUSTED_NETWORK)
        if (adbPort == null || adbPort < MIN_ADB_PORT || !PcMirrorNet.isValidPort(adbPort)) {
            return MirrorPlan.Rejected(MirrorRejection.BAD_ADB_PORT)
        }
        if (pairingPort != null && (pairingPort < MIN_ADB_PORT || !PcMirrorNet.isValidPort(pairingPort))) {
            return MirrorPlan.Rejected(MirrorRejection.BAD_PAIRING_PORT)
        }
        val serial = "$trimmed:$adbPort"
        return MirrorPlan.Ready(
            host = trimmed,
            kind = kind,
            adbPort = adbPort,
            pair = pairingPort?.let { "adb pair $trimmed:$it" },
            connect = "adb connect $serial",
            scrcpy = "scrcpy -s $serial",
            disconnect = "adb disconnect $serial",
        )
    }

    /** Parses a port field. Null for blank or non-numeric input. */
    fun parsePort(text: String?): Int? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty() || t.length > 5 || !t.all { it in '0'..'9' }) return null
        return t.toInt()
    }
}
