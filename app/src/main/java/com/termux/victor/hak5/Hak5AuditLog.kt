package com.termux.victor.hak5

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.FileWriter
import com.termux.victor.utils.IsoTimestamp
import java.util.Date
import java.util.regex.Pattern

/**
 * Append-only JSONL audit log for Hak5 companion events (connects, firmware detection,
 * future API calls). Secrets/tokens are redacted before write. Same spirit as PluginLogger.
 */
object Hak5AuditLog {
    private const val LOG_DIR = "hak5_logs"
    private const val LOG_FILE = "hak5_audit.jsonl"
    private const val MAX_LOG_BYTES = 2L * 1024 * 1024
    private val gson = Gson()

    /** Matches common secret-bearing JSON fields / query params for redaction. */
    private val SECRET_PATTERNS = listOf(
        Pattern.compile("(\"apiToken\"\\s*:\\s*\")([^\"]*)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\"token\"\\s*:\\s*\")([^\"]*)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\"password\"\\s*:\\s*\")([^\"]*)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\"api_token\"\\s*:\\s*\")([^\"]*)(\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(apiToken=)([^&\\s\"]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(Bearer\\s+)(\\S+)", Pattern.CASE_INSENSITIVE)
    )

    data class AuditEvent(
        val timestamp: String,
        val component: String = "hak5",
        val event: String,
        val deviceId: String? = null,
        val firmware: String? = null,
        val detail: String? = null,
        val success: Boolean? = null
    )

    @JvmStatic
    fun redact(text: String?): String? {
        if (text == null) return null
        var out = text
        for (pattern in SECRET_PATTERNS) {
            val matcher = pattern.matcher(out!!)
            val sb = StringBuffer()
            while (matcher.find()) {
                val replacement = when (matcher.groupCount()) {
                    3 -> (matcher.group(1) ?: "") + "***" + (matcher.group(3) ?: "")
                    2 -> (matcher.group(1) ?: "") + "***"
                    else -> "***"
                }
                matcher.appendReplacement(sb, MatcherQuote.quote(replacement))
            }
            matcher.appendTail(sb)
            out = sb.toString()
        }
        return out
    }

    /** Avoid Matcher quoting issues with $ and \ in replacement strings. */
    private object MatcherQuote {
        fun quote(s: String): String = s.replace("\\", "\\\\").replace("$", "\\$")
    }

    @Synchronized
    fun log(
        context: Context,
        event: String,
        detail: String? = null,
        deviceId: String? = null,
        firmware: String? = null,
        success: Boolean? = null
    ) {
        try {
            val dir = File(context.applicationContext.filesDir, LOG_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, LOG_FILE)
            rotateIfNeeded(file)
            val entry = AuditEvent(
                timestamp = IsoTimestamp.format(Date()),
                event = event,
                deviceId = deviceId,
                firmware = firmware,
                detail = redact(detail),
                success = success
            )
            FileWriter(file, true).use { it.appendLine(gson.toJson(entry)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (file.exists() && file.length() > MAX_LOG_BYTES) {
            val backup = File(file.parentFile, "${file.name}.1")
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
        }
    }

    fun readRecent(context: Context, maxLines: Int = 200): List<String> {
        return try {
            val file = File(File(context.applicationContext.filesDir, LOG_DIR), LOG_FILE)
            if (!file.exists()) emptyList() else file.readLines().takeLast(maxLines)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        try {
            val file = File(File(context.applicationContext.filesDir, LOG_DIR), LOG_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
