package com.termux.zerocore.remotecontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Frozen STATUS-TRAY state enum (STATUS_TRAY.md §2). */
enum class StatusTrayState(val wire: String) {
    OK("ok"), DEGRADED("degraded"), DOWN("down"), UNKNOWN("unknown"), OFF("off");

    companion object {
        fun fromWire(value: String): StatusTrayState = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** Frozen STATUS-TRAY domain enum. Unknown wire names decode as OTHER. */
enum class StatusTrayDomain(val wire: String) {
    MESH("mesh"), VPN("vpn"), BROWSER("browser"), SMS("sms"), NODE_HEALTH("node_health"), OTHER("other");

    companion object {
        fun fromWire(value: String): StatusTrayDomain = entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

/** `{ name, state, detail? }`. `detail` max 200 chars, no secrets. */
data class StatusTrayDomainStatus(
    val name: StatusTrayDomain,
    val state: StatusTrayState,
    val detail: String? = null,
    /** Original wire name when it decoded to OTHER (round-trip only). */
    val rawName: String? = null,
) {
    val wireName: String get() = if (name == StatusTrayDomain.OTHER && rawName != null) rawName else name.wire
}

/**
 * Frozen snapshot (STATUS_TRAY.md §2). Proot/Kali container state rides under an
 * `other` domain. Never carries shell history or environment secrets.
 */
data class NodeStatusSnapshot(
    val nodeId: String,
    val packageName: String?,
    val connectionClass: String,
    val updatedAtMs: Long,
    val domains: List<StatusTrayDomainStatus>,
) {
    /** #233 alias: true if any domain ok and none down; false if any down; else null. */
    val healthy: Boolean?
        get() {
            if (domains.any { it.state == StatusTrayState.DOWN }) return false
            if (domains.any { it.state == StatusTrayState.OK }) return true
            return null
        }
}

object NodeStatusPrivacy {
    /** Forbidden key/detail fragments (STATUS_TRAY.md §6), matched case-insensitively. */
    val FORBIDDEN_FRAGMENTS = listOf(
        "password", "passwd", "apikey", "api_key", "apitoken", "api_token", "token",
        "secret", "codeword", "code_word", "whitelist", "signingkey", "signing_key",
        "privatekey", "private_key", "keystore", "cookie", "authorization", "bearer",
        "ssh-rsa", "begin rsa", "begin private", "history",
    )

    private fun fold(s: String) = s.lowercase().replace("_", "").replace("-", "")

    fun assertSafeDetail(detail: String?) {
        if (detail == null) return
        if (detail.length > 200) invalid("domain detail exceeds 200 chars")
        val folded = fold(detail)
        FORBIDDEN_FRAGMENTS.forEach { f ->
            if (folded.contains(fold(f))) invalid("status detail must not carry '$f'")
        }
    }
}

object NodeStatusSnapshotCodec {
    private const val CONNECTION_CLASS_TERMUX = "TERMUX_IPC"
    private val CONNECTION_CLASSES = setOf("EXTERNAL_NODE", "BOUND_COMPANION", "TERMUX_IPC", "HOST_PROCESS_TOOL")

    fun encode(snapshot: NodeStatusSnapshot): String {
        if (snapshot.nodeId.isBlank()) invalid("nodeId is required")
        if (snapshot.connectionClass !in CONNECTION_CLASSES) invalid("unknown connectionClass")
        val domains = JsonArray()
        snapshot.domains.forEach { d ->
            NodeStatusPrivacy.assertSafeDetail(d.detail)
            if (NodeStatusPrivacy.FORBIDDEN_FRAGMENTS.any { d.wireName.lowercase().contains(it) }) {
                invalid("domain name must not carry a secret")
            }
            val o = JsonObject()
            o.addProperty("name", d.wireName)
            o.addProperty("state", d.state.wire)
            d.detail?.let { o.addProperty("detail", it) }
            domains.add(o)
        }
        return JsonObject().apply {
            addProperty("nodeId", snapshot.nodeId)
            if (snapshot.packageName != null) addProperty("packageName", snapshot.packageName) else add("packageName", com.google.gson.JsonNull.INSTANCE)
            addProperty("connectionClass", snapshot.connectionClass)
            addProperty("updatedAtMs", snapshot.updatedAtMs)
            add("domains", domains)
        }.toString()
    }

    /**
     * Build this node's snapshot. Container (proot / Kali / distro) state is
     * reported under an `other` domain. Callers must never pass shell history
     * or env secrets; [encode] refuses anything that looks like one.
     */
    fun buildTermuxSnapshot(
        nodeId: String,
        packageName: String?,
        updatedAtMs: Long,
        nodeHealth: StatusTrayState,
        nodeHealthDetail: String? = null,
        containerState: StatusTrayState? = null,
        containerDetail: String? = null,
    ): NodeStatusSnapshot {
        val domains = mutableListOf(
            StatusTrayDomainStatus(StatusTrayDomain.NODE_HEALTH, nodeHealth, nodeHealthDetail),
        )
        if (containerState != null) {
            domains += StatusTrayDomainStatus(StatusTrayDomain.OTHER, containerState, containerDetail)
        }
        return NodeStatusSnapshot(nodeId, packageName, CONNECTION_CLASS_TERMUX, updatedAtMs, domains)
    }
}

/** One proot-distro / Kali container as seen on disk. [name] is sanitised before use. */
data class ContainerInfo(val name: String, val running: Boolean)

/** Summarise proot/Kali containers into the single `other` domain row. */
object ContainerSummary {
    private val SAFE_NAME = Regex("[^A-Za-z0-9._-]")

    fun summarize(containers: List<ContainerInfo>?): Pair<StatusTrayState, String> {
        if (containers == null) return StatusTrayState.UNKNOWN to "proot: unknown"
        if (containers.isEmpty()) return StatusTrayState.OFF to "proot: none installed"
        val parts = containers.sortedBy { it.name }.map {
            val safe = it.name.replace(SAFE_NAME, "").take(32).ifEmpty { "distro" }
            "$safe ${if (it.running) "running" else "stopped"}"
        }
        val state = if (containers.any { it.running }) StatusTrayState.OK else StatusTrayState.OFF
        var detail = "proot: " + parts.joinToString(", ")
        if (detail.length > 200) detail = detail.take(197) + "..."
        return state to detail
    }
}
