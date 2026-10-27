package com.termux.victor.hak5

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.FileWriter
import com.termux.victor.utils.IsoTimestamp
import java.util.Date

/**
 * Persistent, append-only command log for Hak5 companion actions (plan §5).
 *
 * Distinct from [Hak5AuditLog] (which records lifecycle/connect events): this store
 * records every *executed or blocked action* and its result so the user has a durable,
 * reviewable history. Stored as JSONL in app-private internal storage
 * (filesDir/hak5_logs/hak5_commands.jsonl) — never on external/shared storage.
 *
 * Secrets are redacted via [Hak5AuditLog.redact] before anything is written, so a token
 * accidentally placed in params or echoed back in a response body never lands on disk.
 *
 * The core operates on a plain [File] directory so it is unit-testable without an
 * Android [Context]; Context overloads are provided for app use.
 */
object Hak5CommandLog {
    const val LOG_DIR = "hak5_logs"
    const val LOG_FILE = "hak5_commands.jsonl"
    private const val MAX_LOG_BYTES = 2L * 1024 * 1024
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    /** One executed (or blocked) action and its result. */
    data class CommandLogEntry(
        val timestamp: String,
        /** "system" or "module". */
        val kind: String,
        /** System or module name as discovered at runtime. */
        val target: String,
        val action: String,
        val severity: String,
        /** Redacted parameter summary (may be null). */
        val params: String? = null,
        /** True if the request actually hit the device. */
        val executed: Boolean = false,
        /** True if confirmation/gating blocked the action before any network call. */
        val blocked: Boolean = false,
        val blockReason: String? = null,
        val success: Boolean? = null,
        val httpStatus: Int? = null,
        /** Redacted, truncated response snippet. */
        val responseSnippet: String? = null
    )

    fun dirFor(context: Context): File = File(context.applicationContext.filesDir, LOG_DIR)

    @Synchronized
    fun append(dir: File, entry: CommandLogEntry) {
        try {
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, LOG_FILE)
            rotateIfNeeded(file)
            // Redact per-field first (robust against JSON escaping), then redact the
            // whole serialized line as a backstop before anything touches disk.
            val safe = entry.copy(
                params = Hak5AuditLog.redact(entry.params),
                responseSnippet = Hak5AuditLog.redact(entry.responseSnippet),
                blockReason = Hak5AuditLog.redact(entry.blockReason)
            )
            val line = Hak5AuditLog.redact(gson.toJson(safe)) ?: return
            FileWriter(file, true).use { it.appendLine(line) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun append(context: Context, entry: CommandLogEntry) = append(dirFor(context), entry)

    /** Build a timestamped entry; redaction happens at write time. */
    fun entry(
        kind: String,
        target: String,
        action: String,
        severity: Hak5ActionSeverity,
        params: String? = null,
        executed: Boolean = false,
        blocked: Boolean = false,
        blockReason: String? = null,
        success: Boolean? = null,
        httpStatus: Int? = null,
        responseSnippet: String? = null
    ): CommandLogEntry {
        return CommandLogEntry(
            timestamp = IsoTimestamp.format(Date()),
            kind = kind,
            target = target,
            action = action,
            severity = severity.name,
            params = params?.let { truncate(it, 512) },
            executed = executed,
            blocked = blocked,
            blockReason = blockReason,
            success = success,
            httpStatus = httpStatus,
            responseSnippet = responseSnippet?.let { truncate(it, 1024) }
        )
    }

    fun read(dir: File, max: Int = 200): List<CommandLogEntry> {
        return readRaw(dir, max).mapNotNull { line ->
            try {
                gson.fromJson(line, CommandLogEntry::class.java)
            } catch (_: JsonSyntaxException) {
                null
            }
        }
    }

    fun read(context: Context, max: Int = 200): List<CommandLogEntry> = read(dirFor(context), max)

    fun readRaw(dir: File, max: Int = 200): List<String> {
        return try {
            val file = File(dir, LOG_FILE)
            if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }.takeLast(max)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(dir: File) {
        try {
            val file = File(dir, LOG_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clear(context: Context) = clear(dirFor(context))

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.substring(0, max) + "…(${s.length})"

    private fun rotateIfNeeded(file: File) {
        if (file.exists() && file.length() > MAX_LOG_BYTES) {
            val backup = File(file.parentFile, "${file.name}.1")
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
        }
    }
}
