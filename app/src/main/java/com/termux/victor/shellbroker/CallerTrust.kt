package com.termux.victor.shellbroker

/** One trusted app: package + the SHA-256 of its signing certificate (both must match) + what it may do. */
data class TrustedCaller(val packageName: String, val certSha256: String, val scopes: Set<Scope>, val label: String = packageName)

/**
 * Owner-managed allow-list. A row is created only by the owner in VictorSuite (it shows the certificate fingerprint to confirm).
 * Keeping it separate from the Termux RUN_COMMAND allow-list is deliberate: trusting an app to run `RUN_COMMAND` never grants
 * it the broker, and PRIVILEGED is a distinct scope that nothing else implies.
 */
interface TrustStore {
    fun all(): List<TrustedCaller>
    fun find(packageName: String): TrustedCaller?
    fun upsert(c: TrustedCaller)
    fun remove(packageName: String)
    /** Called after any change so the broker can end sessions that lost their basis immediately. */
    fun addListener(l: () -> Unit)
}

class InMemoryTrustStore(initial: List<TrustedCaller> = emptyList()) : TrustStore {
    private val rows = LinkedHashMap<String, TrustedCaller>().also { m -> initial.forEach { m[it.packageName] = it } }
    private val listeners = mutableListOf<() -> Unit>()
    @Synchronized override fun all() = rows.values.toList()
    @Synchronized override fun find(packageName: String) = rows[packageName]
    override fun upsert(c: TrustedCaller) { synchronized(this) { rows[c.packageName] = c }; fire() }
    override fun remove(packageName: String) { synchronized(this) { rows.remove(packageName) }; fire() }
    @Synchronized override fun addListener(l: () -> Unit) { listeners += l }
    private fun fire() { val ls = synchronized(this) { listeners.toList() }; ls.forEach { it() } }
}

/** What the OS tells us about installed packages. Android implementation: PackageManager (+ signing info). */
interface PackageIdentity {
    fun packagesForUid(uid: Int): List<String>
    /** SHA-256 (lowercase hex) of the signing certificate(s); null when unreadable or the package has rotated/multiple ambiguous signers. */
    fun certSha256(packageName: String): String?
}

sealed class Verification {
    data class Ok(val caller: VerifiedCaller) : Verification()
    data class Rejected(val code: RejectCode, val reason: String) : Verification()
}

/**
 * Binder-uid → package → pinned certificate. Fail-closed on every doubt: shared uids with several packages are refused (we cannot
 * tell which one is calling), an unreadable or different certificate is refused, and a caller never gets a scope it was not given.
 */
class CallerVerifier(private val ids: PackageIdentity, private val trust: TrustStore) {
    fun verify(uid: Int, need: Scope): Verification {
        val pkgs = ids.packagesForUid(uid)
        if (pkgs.isEmpty()) return Verification.Rejected(RejectCode.UNKNOWN_CALLER, "caller uid $uid has no package")
        if (pkgs.size != 1) return Verification.Rejected(RejectCode.AMBIGUOUS_CALLER, "caller uid is shared by several packages")
        val pkg = pkgs.single()
        val row = trust.find(pkg) ?: return Verification.Rejected(RejectCode.UNKNOWN_CALLER, "'$pkg' is not trusted by VictorSuite's owner")
        val cert = ids.certSha256(pkg) ?: return Verification.Rejected(RejectCode.UNTRUSTED_CERT, "could not read the signing certificate of '$pkg'")
        if (!norm(cert).equals(norm(row.certSha256), ignoreCase = true))
            return Verification.Rejected(RejectCode.UNTRUSTED_CERT, "the signing certificate of '$pkg' does not match the one the owner trusted")
        if (need !in row.scopes) return Verification.Rejected(if (need == Scope.PRIVILEGED) RejectCode.PRIVILEGE_NOT_GRANTED else RejectCode.SCOPE_NOT_GRANTED,
            "'$pkg' was not granted ${need.name.lowercase()} access")
        return Verification.Ok(VerifiedCaller(uid, pkg, norm(cert).lowercase(), row.scopes, row.label))
    }

    /**
     * The reverse direction: is [packageName] (the app VictorSuite is about to bind for remote terminals) the transport provider the
     * owner trusted? Same pin: package + signing certificate + the TRANSPORT_PROVIDER scope.
     */
    fun verifyProvider(packageName: String): Verification {
        val row = trust.find(packageName) ?: return Verification.Rejected(RejectCode.UNKNOWN_CALLER, "'$packageName' is not trusted as a remote-terminal transport")
        val cert = ids.certSha256(packageName) ?: return Verification.Rejected(RejectCode.UNTRUSTED_CERT, "could not read the signing certificate of '$packageName'")
        if (!norm(cert).equals(norm(row.certSha256), ignoreCase = true)) return Verification.Rejected(RejectCode.UNTRUSTED_CERT, "'$packageName' is signed by a different certificate than the one you trusted")
        if (Scope.TRANSPORT_PROVIDER !in row.scopes) return Verification.Rejected(RejectCode.SCOPE_NOT_GRANTED, "'$packageName' was not trusted as a terminal transport")
        return Verification.Ok(VerifiedCaller(-1, packageName, norm(cert).lowercase(), row.scopes, row.label))
    }

    private fun norm(s: String) = s.replace(":", "").trim()
}
