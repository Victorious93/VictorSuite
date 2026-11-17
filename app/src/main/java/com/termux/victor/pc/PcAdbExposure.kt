package com.termux.victor.pc

/**
 * What the phone's ADB looks like right now. Values come from `Settings.Global` and `getprop`;
 * null means "could not be read" (some vendors hide these), never "off".
 */
data class PcAdbExposure(
    val wirelessDebuggingOn: Boolean?,
    val usbDebuggingOn: Boolean?,
    val legacyTcpPort: Int?,
) {
    /** The old `adb tcpip` / `service.adb.tcp.port` listener, which has no pairing step. */
    val legacyTcpListenerOpen: Boolean get() = legacyTcpPort != null

    companion object {
        /** `getprop` prints an empty line, `-1` or `0` when no TCP port is set. */
        fun parseTcpPort(raw: String?): Int? {
            val t = raw?.trim().orEmpty()
            if (t.isEmpty() || t.length > 5 || !t.all { it in '0'..'9' }) return null
            val port = t.toInt()
            return if (PcMirrorNet.isValidPort(port)) port else null
        }

        /** `service.adb.tcp.port` wins over `persist.adb.tcp.port`, like adbd. */
        fun legacyTcpPort(serviceProp: String?, persistProp: String?): Int? =
            parseTcpPort(serviceProp) ?: parseTcpPort(persistProp)

        /** Settings.Global ints: 1 = on, 0 = off, anything else = unknown. */
        fun flag(value: Int?): Boolean? = when (value) {
            1 -> true
            0 -> false
            else -> null
        }
    }
}
