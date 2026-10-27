package com.termux.victor.remotecontrol

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Frozen SETTINGS/BACKUP bundle (droidcommand-AI `docs/specs/UNIFIED_BACKUP.md`),
 * node side for VictorSuite (`com.termux`).
 *
 * VictorSuite entry payload, `schemaVersion` 1:
 * ```
 * { "termuxProperties": {k:v}, "colorsProperties": {k:v},
 *   "remoteControl": { "externalControlEnabled": false, "pairings": [ {sourceNodeRef, packageName, certSha256?} ] } }
 * ```
 * Only allow-listed files are ever read. Shell history, env files / dotfiles,
 * SSH keys and anything matching the secret-name list are never exported.
 */
enum class BackupWrapping(val wire: String) {
    RAW_AES_256_GCM("raw-aes-256-gcm"),
    ANDROID_KEYSTORE("android-keystore"),
    NONE("none");

    companion object {
        fun fromWire(v: String?): BackupWrapping? = entries.firstOrNull { it.wire == v }
    }
}

enum class BackupConflictPolicy(val wire: String) {
    SKIP("skip"), OVERWRITE("overwrite"), MERGE_BY_ID("merge-by-id");

    companion object {
        fun fromWire(v: String?): BackupConflictPolicy? =
            if (v.isNullOrBlank()) SKIP else entries.firstOrNull { it.wire == v }
    }
}

data class UnifiedBackupEntry(
    val packageName: String,
    val schemaVersion: Int,
    val ciphertext: String? = null,
    val clearPayload: String? = null,
)

data class UnifiedFleetBackup(
    val version: Int,
    val createdAtMs: Long,
    val sourceNodeRef: String,
    val wrapping: BackupWrapping,
    val entries: List<UnifiedBackupEntry>,
)

/** AES-GCM seam. Android Keystore wrapping is documented, not implemented in this PR. */
interface FleetBackupAead {
    fun encrypt(plaintext: ByteArray, aad: ByteArray): String
    fun decrypt(envelope: String, aad: ByteArray): ByteArray
}

/** AES-256-GCM, 12-byte IV, 128-bit tag, record `v1.<b64url iv>.<b64url ct+tag>` (no padding). */
class JceFleetBackupAead(private val key: SecretKey) : FleetBackupAead {
    override fun encrypt(plaintext: ByteArray, aad: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected GCM IV size" }
        cipher.updateAAD(aad)
        val ct = cipher.doFinal(plaintext)
        val b64 = Base64.getUrlEncoder().withoutPadding()
        return "v1.${b64.encodeToString(iv)}.${b64.encodeToString(ct)}"
    }

    override fun decrypt(envelope: String, aad: ByteArray): ByteArray {
        val parts = envelope.split('.')
        require(parts.size == 3 && parts[0] == "v1") { "malformed ciphertext" }
        val dec = Base64.getUrlDecoder()
        val iv = dec.decode(parts[1])
        val ct = dec.decode(parts[2])
        require(iv.size == IV_BYTES && ct.size >= 16) { "malformed ciphertext" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ct)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/** Secret-name guard for backup payloads (UNIFIED_BACKUP.md §3 "Never in a payload"). Refuses; never strips. */
object BackupPrivacy {
    val FORBIDDEN_KEY_FRAGMENTS = listOf(
        "password", "passwd", "apikey", "api_key", "apitoken", "api_token", "token", "secret",
        "codeword", "code_word", "whitelist", "signingkey", "signing_key", "privatekey", "private_key",
        "keystore", "cookie", "authorization", "bearer",
        // VictorSuite node additions: never shell history or SSH material.
        "history", "ssh", "id_rsa", "id_ed25519", "id_ecdsa", "authorized_keys", "known_hosts",
    )

    /** VictorSpoof creds / number pool: case-insensitive substrings anywhere. */
    val SPOOF_SUBSTRINGS = listOf("twilio", "authtoken", "numberpool")

    /** Content markers that must never appear anywhere in a payload. */
    private val CONTENT_MARKERS = listOf("-----begin", "ssh-rsa ", "ssh-ed25519 ", "ecdsa-sha2-")

    private fun fold(s: String) = s.lowercase().replace("_", "").replace("-", "")

    fun assertSafeJson(raw: String) {
        val compact = fold(raw)
        SPOOF_SUBSTRINGS.forEach { if (compact.contains(it)) invalid("backup payload must not carry '$it'") }
        val lower = raw.lowercase()
        CONTENT_MARKERS.forEach { if (lower.contains(it)) invalid("backup payload must not carry key material") }
        val root = try {
            JsonParser.parseString(raw)
        } catch (e: RuntimeException) {
            invalid("backup payload is not valid JSON")
        }
        scanKeys(root)
    }

    private fun scanKeys(el: JsonElement) {
        when {
            el.isJsonObject -> el.asJsonObject.entrySet().forEach { (k, v) ->
                val fk = fold(k)
                FORBIDDEN_KEY_FRAGMENTS.forEach { f -> if (fk.contains(fold(f))) invalid("backup payload must not carry '$f'") }
                scanKeys(v)
            }
            el.isJsonArray -> el.asJsonArray.forEach { scanKeys(it) }
        }
    }
}

/**
 * Read side used by export and by import (for the live values). The Android
 * implementation reads ONLY the allow-listed files under `~/.termux/`.
 */
interface NodeConfigSource {
    /** Candidate files by path relative to `$HOME` -> content. May include files that must be excluded. */
    fun files(): Map<String, String>
    fun remoteControlConfig(): RemoteControlConfig
}

/** Write side for an applied (non-dry-run, confirmed) import. */
interface NodeConfigSink {
    fun writeTermuxProperties(values: Map<String, String>)
    fun writeColorsProperties(values: Map<String, String>)
    fun saveRemoteControlConfig(config: RemoteControlConfig)
}

data class BackupImportReport(
    val dryRun: Boolean,
    val applied: Boolean,
    val policy: BackupConflictPolicy,
    val packageName: String,
    val added: List<String>,
    val skipped: List<String>,
    val overwritten: List<String>,
    val ignored: List<String>,
    val externalControlForcedOff: Boolean = true,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("dryRun", dryRun)
        addProperty("applied", applied)
        addProperty("conflictPolicy", policy.wire)
        add("packages", JsonArray().also { it.add(packageName) })
        add("added", JsonArray().also { a -> added.forEach { a.add(it) } })
        add("skipped", JsonArray().also { a -> skipped.forEach { a.add(it) } })
        add("overwritten", JsonArray().also { a -> overwritten.forEach { a.add(it) } })
        add("ignored", JsonArray().also { a -> ignored.forEach { a.add(it) } })
        addProperty("externalControlForcedOff", externalControlForcedOff)
    }
}

object NodeConfigBackup {
    const val BUNDLE_VERSION = 1
    const val SCHEMA_VERSION = 1
    const val NODE_PACKAGE = "com.termux"

    const val TERMUX_PROPERTIES = ".termux/termux.properties"
    const val COLORS_PROPERTIES = ".termux/colors.properties"

    /** The ONLY files that are ever exported. */
    val EXPORT_ALLOWLIST = setOf(TERMUX_PROPERTIES, COLORS_PROPERTIES)

    /** Defense in depth: even if the allow-list grows, these are never exported. */
    private val DENY_PATH_FRAGMENTS = listOf(
        "history", ".ssh/", "id_rsa", "id_ed25519", "id_ecdsa", "authorized_keys", "known_hosts",
        ".env", "termux.env", ".bashrc", ".zshrc", ".profile", ".bash_profile", ".netrc",
        ".git-credentials", ".gnupg", ".pgpass", "keystore", "secret", "token",
    )

    /** termux.properties keys an import may never change (they widen external access). */
    val IMPORT_PROTECTED_PROPERTY_KEYS = setOf("allow-external-apps")

    fun isExportable(path: String): Boolean {
        val p = path.trimStart('/')
        if (p !in EXPORT_ALLOWLIST) return false
        val lower = p.lowercase()
        return DENY_PATH_FRAGMENTS.none { lower.contains(it) }
    }

    fun aad(packageName: String, schemaVersion: Int, sourceNodeRef: String): ByteArray =
        "$packageName|$schemaVersion|$sourceNodeRef".toByteArray(Charsets.UTF_8)

    /** Build this node's payload JSON (no secrets, no history, no SSH keys). */
    fun buildPayload(source: NodeConfigSource): String {
        val files = source.files()
        val payload = JsonObject()
        files[TERMUX_PROPERTIES]?.takeIf { isExportable(TERMUX_PROPERTIES) }?.let {
            payload.add("termuxProperties", propsToJson(parseProperties(it)))
        }
        files[COLORS_PROPERTIES]?.takeIf { isExportable(COLORS_PROPERTIES) }?.let {
            payload.add("colorsProperties", propsToJson(parseProperties(it)))
        }
        val rc = source.remoteControlConfig()
        payload.add("remoteControl", JsonObject().apply {
            // Exported as-is for the record; import always forces it false.
            addProperty("externalControlEnabled", rc.externalControlEnabled)
            add("pairings", JsonArray().also { arr ->
                rc.pairings.forEach { p ->
                    arr.add(JsonObject().apply {
                        addProperty("sourceNodeRef", p.sourceNodeRef)
                        addProperty("packageName", p.packageName)
                        p.certSha256?.let { addProperty("certSha256", it) }
                    })
                }
            })
        })
        val raw = payload.toString()
        BackupPrivacy.assertSafeJson(raw)
        return raw
    }

    /**
     * `config.export`: one-entry bundle for this node. Production encrypts with
     * [aead] (`raw-aes-256-gcm`); [clearPayload] = local-dev only (`wrapping=none`).
     */
    fun export(source: NodeConfigSource, sourceNodeRef: String, nowMs: Long, aead: FleetBackupAead?, clearPayload: Boolean): String {
        val payload = buildPayload(source)
        val entry = if (clearPayload) {
            UnifiedBackupEntry(NODE_PACKAGE, SCHEMA_VERSION, clearPayload = payload)
        } else {
            val cipher = aead ?: throw RemoteControlException(RemoteErrorCategory.INVALID_PARAMETERS, "encrypted export needs a key")
            UnifiedBackupEntry(NODE_PACKAGE, SCHEMA_VERSION,
                ciphertext = cipher.encrypt(payload.toByteArray(Charsets.UTF_8), aad(NODE_PACKAGE, SCHEMA_VERSION, sourceNodeRef)))
        }
        val bundle = UnifiedFleetBackup(BUNDLE_VERSION, nowMs, sourceNodeRef,
            if (clearPayload) BackupWrapping.NONE else BackupWrapping.RAW_AES_256_GCM, listOf(entry))
        return encodeBundle(bundle)
    }

    fun encodeBundle(b: UnifiedFleetBackup): String = JsonObject().apply {
        addProperty("version", b.version)
        addProperty("createdAtMs", b.createdAtMs)
        addProperty("sourceNodeRef", b.sourceNodeRef)
        addProperty("wrapping", b.wrapping.wire)
        add("entries", JsonArray().also { arr ->
            b.entries.forEach { e ->
                arr.add(JsonObject().apply {
                    addProperty("packageName", e.packageName)
                    addProperty("schemaVersion", e.schemaVersion)
                    e.ciphertext?.let { addProperty("ciphertext", it) }
                    e.clearPayload?.let { addProperty("clearPayload", it) }
                })
            }
        })
    }.toString()

    /** Steps 1-2 of UNIFIED_BACKUP.md §4 plus entry shape rules. All failures are invalid_parameters. */
    fun decodeBundle(raw: String?): UnifiedFleetBackup {
        if (raw.isNullOrBlank()) invalid("bundle is required")
        val root = try { JsonParser.parseString(raw) } catch (e: RuntimeException) { invalid("bundle is not valid JSON") }
        if (!root.isJsonObject) invalid("bundle must be an object")
        val o = root.asJsonObject
        val version = o.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: invalid("bundle version is required")
        if (version != BUNDLE_VERSION) invalid("unsupported bundle version $version")
        val wrapping = BackupWrapping.fromWire(o.get("wrapping")?.takeIf { it.isJsonPrimitive }?.asString) ?: invalid("unknown wrapping")
        val source = o.get("sourceNodeRef")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: invalid("sourceNodeRef is required")
        val created = o.get("createdAtMs")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L
        val entriesEl = o.get("entries")?.takeIf { it.isJsonArray }?.asJsonArray ?: invalid("entries is required")
        if (entriesEl.size() == 0) invalid("bundle needs at least one entry")
        val entries = entriesEl.map { el ->
            if (!el.isJsonObject) invalid("entry must be an object")
            val e = el.asJsonObject
            val pkg = e.get("packageName")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: invalid("entry packageName is required")
            val sv = e.get("schemaVersion")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: invalid("entry schemaVersion is required")
            if (sv <= 0) invalid("schemaVersion must be positive")
            val ct = e.get("ciphertext")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            val clear = e.get("clearPayload")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            if ((ct == null) == (clear == null)) invalid("entry needs exactly one of ciphertext or clearPayload")
            if (wrapping == BackupWrapping.NONE && clear == null) invalid("wrapping=none requires clearPayload")
            if (wrapping != BackupWrapping.NONE && ct == null) invalid("wrapping=${wrapping.wire} requires ciphertext")
            UnifiedBackupEntry(pkg, sv, ct, clear)
        }
        if (entries.map { it.packageName }.toSet().size != entries.size) invalid("duplicate packageName in bundle")
        return UnifiedFleetBackup(version, created, source, wrapping, entries)
    }

    /**
     * `config.import` (UNIFIED_BACKUP.md §4). Order: parse -> duplicates -> decrypt
     * -> secret scan -> dry-run report | (confirm required) apply -> force external
     * control OFF. [confirmed] must come from an on-device confirmation, never from
     * the envelope.
     */
    fun import(
        rawBundle: String?,
        live: NodeConfigSource,
        sink: NodeConfigSink?,
        aead: FleetBackupAead?,
        policy: BackupConflictPolicy,
        dryRun: Boolean,
        confirmed: Boolean,
    ): BackupImportReport {
        val bundle = decodeBundle(rawBundle)
        val entry = bundle.entries.firstOrNull { it.packageName == NODE_PACKAGE }
            ?: throw RemoteControlException(RemoteErrorCategory.NOT_FOUND, "no entry for $NODE_PACKAGE")
        if (entry.schemaVersion != SCHEMA_VERSION) invalid("unsupported $NODE_PACKAGE schemaVersion ${entry.schemaVersion}")
        val payload = when {
            entry.clearPayload != null -> entry.clearPayload
            else -> {
                val cipher = aead ?: invalid("ciphertext entry needs a key")
                try {
                    String(cipher.decrypt(entry.ciphertext!!, aad(entry.packageName, entry.schemaVersion, bundle.sourceNodeRef)), Charsets.UTF_8)
                } catch (e: RemoteControlException) {
                    throw e
                } catch (e: Exception) {
                    throw RemoteControlException(RemoteErrorCategory.INTERNAL, "backup entry failed to decrypt (tamper or wrong key)")
                }
            }
        }
        BackupPrivacy.assertSafeJson(payload)
        val p = JsonParser.parseString(payload).takeIf { it.isJsonObject }?.asJsonObject ?: invalid("payload must be an object")

        val added = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val overwritten = mutableListOf<String>()
        val ignored = mutableListOf<String>()

        val liveFiles = live.files()
        val liveTermux = liveFiles[TERMUX_PROPERTIES]?.let { parseProperties(it) } ?: emptyMap()
        val liveColors = liveFiles[COLORS_PROPERTIES]?.let { parseProperties(it) } ?: emptyMap()
        val liveRc = live.remoteControlConfig()

        val inTermux = jsonToProps(p.get("termuxProperties")).toMutableMap()
        IMPORT_PROTECTED_PROPERTY_KEYS.forEach { k -> if (inTermux.remove(k) != null) ignored += "termuxProperties:$k" }
        val mergedTermux = mergeMap("termuxProperties", liveTermux, inTermux, policy, added, skipped, overwritten)
        val mergedColors = mergeMap("colorsProperties", liveColors, jsonToProps(p.get("colorsProperties")), policy, added, skipped, overwritten)

        val inPairings = pairingsOf(p.get("remoteControl"))
        val mergedPairings = mergeList("pairing", liveRc.pairings, inPairings, { it.sourceNodeRef }, policy, added, skipped, overwritten)
        // Step 7: never auto-enable external control / remote.command, whatever the payload or policy says.
        val mergedRc = RemoteControlConfig(externalControlEnabled = false, pairings = mergedPairings)
        if (p.get("remoteControl")?.isJsonObject == true &&
            p.getAsJsonObject("remoteControl").get("externalControlEnabled")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
        ) {
            ignored += "remoteControl:externalControlEnabled"
        }

        if (dryRun) {
            return BackupImportReport(true, false, policy, NODE_PACKAGE, added, skipped, overwritten, ignored)
        }
        if (!confirmed) {
            throw RemoteControlException(RemoteErrorCategory.CONFIRMATION_REQUIRED, "config.import needs on-device confirmation (or dryRun)")
        }
        val out = sink ?: throw RemoteControlException(RemoteErrorCategory.INTERNAL, "no config sink")
        if (inTermux.isNotEmpty()) out.writeTermuxProperties(mergedTermux)
        if (p.has("colorsProperties")) out.writeColorsProperties(mergedColors)
        out.saveRemoteControlConfig(mergedRc)
        return BackupImportReport(false, true, policy, NODE_PACKAGE, added, skipped, overwritten, ignored)
    }

    private fun mergeMap(
        label: String, live: Map<String, String>, incoming: Map<String, String>, policy: BackupConflictPolicy,
        added: MutableList<String>, skipped: MutableList<String>, overwritten: MutableList<String>,
    ): Map<String, String> {
        val out = LinkedHashMap(live)
        incoming.forEach { (k, v) ->
            when {
                k !in live -> { out[k] = v; added += "$label:$k" }
                policy == BackupConflictPolicy.OVERWRITE -> { out[k] = v; overwritten += "$label:$k" }
                else -> skipped += "$label:$k"
            }
        }
        return out
    }

    private fun <T> mergeList(
        label: String, live: List<T>, incoming: List<T>, idOf: (T) -> String, policy: BackupConflictPolicy,
        added: MutableList<String>, skipped: MutableList<String>, overwritten: MutableList<String>,
    ): List<T> {
        val out = LinkedHashMap<String, T>()
        live.forEach { out[idOf(it)] = it }
        incoming.forEach { row ->
            val id = idOf(row)
            when {
                id !in out -> { out[id] = row; added += "$label:$id" }
                policy == BackupConflictPolicy.OVERWRITE -> { out[id] = row; overwritten += "$label:$id" }
                else -> skipped += "$label:$id"
            }
        }
        return out.values.toList()
    }

    private fun pairingsOf(el: JsonElement?): List<DevicePairing> {
        if (el == null || !el.isJsonObject) return emptyList()
        val arr = el.asJsonObject.get("pairings")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { r ->
            if (!r.isJsonObject) return@mapNotNull null
            val o = r.asJsonObject
            val src = o.get("sourceNodeRef")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val pkg = o.get("packageName")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            if (src.isEmpty() || pkg.isEmpty()) invalid("pairing needs sourceNodeRef and packageName")
            DevicePairing(src, pkg, o.get("certSha256")?.takeIf { it.isJsonPrimitive }?.asString)
        }
    }

    private fun propsToJson(m: Map<String, String>) = JsonObject().also { o -> m.forEach { (k, v) -> o.addProperty(k, v) } }

    private fun jsonToProps(el: JsonElement?): Map<String, String> {
        if (el == null || el.isJsonNull) return emptyMap()
        if (!el.isJsonObject) invalid("properties section must be an object")
        val out = LinkedHashMap<String, String>()
        el.asJsonObject.entrySet().forEach { (k, v) ->
            if (!v.isJsonPrimitive) invalid("property '$k' must be a string")
            if (!isSafePropertyKey(k)) invalid("property key '${k.take(40)}' has whitespace, separators or control characters")
            val value = v.asString
            if (value.any { it == '\n' || it == '\r' }) invalid("property '$k' value must be a single line")
            out[k] = value
        }
        return out
    }

    /** A key that `java.util.Properties` reads back unchanged: no whitespace, separators, `\`, or control chars. */
    fun isSafePropertyKey(key: String): Boolean =
        key.isNotEmpty() && key.none { it.isWhitespace() || it.isISOControl() || it in "=:\\#!" }

    /** Parses with `java.util.Properties` (what Termux uses): `\` continuations, escapes, `=`/`:`/space separators. */
    fun parseProperties(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        object : java.util.Properties() {
            @Synchronized
            override fun put(key: Any, value: Any): Any? {
                out[key as String] = value as String
                return super.put(key, value)
            }
        }.load(java.io.StringReader(text))
        return out
    }

    /** Inverse of [parseProperties]: keys and values are escaped so `Properties.load` returns them exactly. */
    fun renderProperties(values: Map<String, String>, header: String): String = buildString {
        append("# ").append(header.replace('\n', ' ').replace('\r', ' ')).append('\n')
        values.forEach { (k, v) -> append(escapeProp(k, true)).append('=').append(escapeProp(v, false)).append('\n') }
    }

    private fun escapeProp(s: String, isKey: Boolean): String = buildString {
        s.forEachIndexed { i, c ->
            when (c) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u000c' -> append("\\f")
                ' ' -> if (isKey || i == 0) append("\\ ") else append(c)
                '=', ':', '#', '!' -> if (isKey || (i == 0 && (c == '#' || c == '!'))) append('\\').append(c) else append(c)
                else -> append(c)
            }
        }
    }
}
