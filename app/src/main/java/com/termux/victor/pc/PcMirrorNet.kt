package com.termux.victor.pc

/** What kind of network an address belongs to, for the "PC mirrors this phone" flow. */
enum class AddrKind {
    /** RFC 1918 private range: a home / office Wi-Fi or LAN. */
    LAN,

    /** 100.64.0.0/10 (carrier-grade NAT space), which Tailscale uses for tailnet addresses. */
    TAILSCALE,
    LOOPBACK,
    LINK_LOCAL,

    /** Anything routable on the internet. Never acceptable for ADB. */
    PUBLIC,
    INVALID,
}

/** One IPv4 address on this phone, with the interface it belongs to. */
data class LocalAddress(
    val iface: String,
    val ip: String,
    val kind: AddrKind,
    val mobileData: Boolean,
) {
    /** Offered as a mirroring host: a trusted network, and not the carrier's mobile data. */
    val usable: Boolean get() = !mobileData && PcMirrorNet.isTrusted(kind)
}

/**
 * Pure address helpers. No DNS and no Android calls, so they are unit-testable on the JVM.
 *
 * Only dotted-quad IPv4 is accepted. Host names are refused on purpose: a name can resolve to a
 * public address, and the commands built from these values are meant to be pasted into a shell.
 */
object PcMirrorNet {

    /** Wi-Fi and Tailscale are the only networks this feature is meant for. */
    fun isTrusted(kind: AddrKind): Boolean = kind == AddrKind.LAN || kind == AddrKind.TAILSCALE

    /** Returns the four octets, or null unless [host] is a strict dotted-quad (no leading zeros). */
    fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for ((i, part) in parts.withIndex()) {
            if (part.isEmpty() || part.length > 3) return null
            if (part.length > 1 && part[0] == '0') return null
            if (!part.all { it in '0'..'9' }) return null
            val value = part.toInt()
            if (value > 255) return null
            octets[i] = value
        }
        return octets
    }

    fun classify(host: String): AddrKind {
        val o = parseIpv4(host) ?: return AddrKind.INVALID
        return when {
            o[0] == 10 -> AddrKind.LAN
            o[0] == 172 && o[1] in 16..31 -> AddrKind.LAN
            o[0] == 192 && o[1] == 168 -> AddrKind.LAN
            o[0] == 100 && o[1] in 64..127 -> AddrKind.TAILSCALE
            o[0] == 127 -> AddrKind.LOOPBACK
            o[0] == 169 && o[1] == 254 -> AddrKind.LINK_LOCAL
            o[0] == 0 -> AddrKind.INVALID
            o[0] >= 224 -> AddrKind.INVALID
            else -> AddrKind.PUBLIC
        }
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    /**
     * Mobile-data interfaces often carry private-looking carrier-grade NAT addresses (10.x or
     * 100.64.x). They are not a LAN and never reachable from the PC, so they are not offered.
     */
    fun isMobileInterface(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("rmnet") || n.startsWith("v4-rmnet") || n.startsWith("ccmni") ||
            n.startsWith("pdp") || n.startsWith("ppp") || n.startsWith("wwan") || n.startsWith("clat")
    }

    fun describe(iface: String, ip: String): LocalAddress =
        LocalAddress(iface, ip, classify(ip), isMobileInterface(iface))

    /** Usable hosts, Wi-Fi/LAN first, then Tailscale. */
    fun usableHosts(addresses: List<LocalAddress>): List<LocalAddress> =
        addresses.filter { it.usable }.sortedBy { if (it.kind == AddrKind.LAN) 0 else 1 }
}
