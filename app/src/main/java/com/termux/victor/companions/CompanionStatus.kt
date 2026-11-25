package com.termux.victor.companions

/** What VictorSuite can tell about one companion on this device. */
enum class CompanionState {
    /** Not installed. VictorSuite carries on without it. */
    NOT_INSTALLED,

    /** Installed and able to share VictorSuite's user id (same signing key). */
    READY,

    /** Installed but signed with a different key, so the shared-user plugin link can't work. */
    SIGNATURE_MISMATCH,

    /** Installed; this companion doesn't need the shared user, so it works over intents. */
    INSTALLED_STANDALONE,
}

data class CompanionStatus(
    val spec: CompanionSpec,
    val state: CompanionState,
    val packageId: String?,
    val versionName: String?,
)

/** Abstraction over PackageManager so the logic is unit-testable. */
interface PackageLookup {
    /** Returns the installed versionName (or "" when unknown), or null when not installed. */
    fun installedVersion(packageId: String): String?

    /** True when [packageId] is signed with the same certificate as VictorSuite. */
    fun signatureMatchesSelf(packageId: String): Boolean
}

object CompanionDetector {
    fun detect(spec: CompanionSpec, lookup: PackageLookup): CompanionStatus {
        for (pkg in spec.packageIds) {
            val version = lookup.installedVersion(pkg) ?: continue
            val state = when {
                !spec.needsSharedUser -> CompanionState.INSTALLED_STANDALONE
                lookup.signatureMatchesSelf(pkg) -> CompanionState.READY
                else -> CompanionState.SIGNATURE_MISMATCH
            }
            return CompanionStatus(spec, state, pkg, version.ifEmpty { null })
        }
        return CompanionStatus(spec, CompanionState.NOT_INSTALLED, null, null)
    }

    fun detectAll(lookup: PackageLookup, catalog: List<CompanionSpec> = CompanionCatalog.ALL): List<CompanionStatus> =
        catalog.map { detect(it, lookup) }

    /** One line per companion, for a status screen or a bug report. */
    fun report(statuses: List<CompanionStatus>): String = statuses.joinToString("\n") { s ->
        val where = listOfNotNull(s.packageId, s.versionName).joinToString(" ")
        val tail = when (s.state) {
            CompanionState.NOT_INSTALLED -> "not installed (optional)"
            CompanionState.READY -> "ready ($where)"
            CompanionState.INSTALLED_STANDALONE -> "installed ($where)"
            CompanionState.SIGNATURE_MISMATCH -> "installed but signed with a different key; reinstall the matching build"
        }
        "${s.spec.displayName}: $tail"
    }
}
