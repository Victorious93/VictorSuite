package com.termux.zerocore.remotecontrol

/**
 * Transport-authenticated caller. On TERMUX_IPC this is derived from the
 * `com.termux.permission.RUN_COMMAND` permission + the VictorSuite
 * allow-external-apps allow-list + the calling package. NEVER from a payload
 * field. [sourceNodeRef] is derived from the authenticated caller.
 */
data class SourceIdentity(
    val sourceNodeRef: String,
    val packageName: String?,
    val certSha256: String? = null,
) {
    init {
        require(sourceNodeRef.isNotBlank()) { "sourceNodeRef is required" }
    }
}

/** A paired source node. Empty [certSha256] = fail-open / unpinned (cert TBD). */
data class DevicePairing(
    val sourceNodeRef: String,
    val packageName: String,
    val certSha256: String? = null,
)

/**
 * Pairing table / allow-list. Caller-id is checked here, before anything else
 * in the trust pipeline. Lookup is by the **authenticated** calling package;
 * the resulting [SourceIdentity.sourceNodeRef] comes from the pairing row,
 * never from the envelope.
 */
class PairingTable(pairings: List<DevicePairing> = emptyList()) {
    private val byPackage: Map<String, List<DevicePairing>> = pairings.groupBy { it.packageName }

    /**
     * @throws RemoteControlException NOT_PAIRED when the calling package has no
     *   pairing row, UNAUTHORIZED when the row is cert-pinned and the caller's
     *   cert is missing or different, or the package is ambiguous.
     */
    fun authenticate(callingPackage: String?, callerCertSha256: String?): SourceIdentity {
        if (callingPackage.isNullOrBlank()) {
            throw RemoteControlException(RemoteErrorCategory.UNAUTHORIZED, "calling package could not be authenticated")
        }
        val rows = byPackage[callingPackage].orEmpty()
        if (rows.isEmpty()) {
            throw RemoteControlException(RemoteErrorCategory.NOT_PAIRED, "package '$callingPackage' is not paired")
        }
        if (rows.size > 1) {
            throw RemoteControlException(RemoteErrorCategory.UNAUTHORIZED, "package '$callingPackage' has more than one pairing row")
        }
        val pairing = rows.single()
        val pinned = pairing.certSha256?.takeIf { it.isNotBlank() }
        if (pinned != null && !normalizeCert(pinned).equals(normalizeCert(callerCertSha256), ignoreCase = true)) {
            throw RemoteControlException(RemoteErrorCategory.UNAUTHORIZED, "signing cert does not match pairing")
        }
        return SourceIdentity(pairing.sourceNodeRef, callingPackage, callerCertSha256)
    }

    private fun normalizeCert(value: String?): String = value.orEmpty().replace(":", "").trim()
}

/** The node's persisted remote-control config. External control is OFF by default. */
data class RemoteControlConfig(
    val externalControlEnabled: Boolean = false,
    val pairings: List<DevicePairing> = emptyList(),
) {
    fun pairingTable(): PairingTable = PairingTable(pairings)
}
