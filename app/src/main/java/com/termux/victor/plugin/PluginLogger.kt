package com.termux.victor.plugin

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.FileWriter
import com.termux.victor.utils.IsoTimestamp
import java.util.Date

/**
 * Append-only, newline-delimited-JSON audit log for the plugin framework: registration,
 * authorized-use acknowledgment, enable/disable, and launch events. Kept separate from
 * the app's general debug logging so plugin activity has its own reviewable trail.
 */
object PluginLogger {
    private const val LOG_DIR = "plugin_logs"
    private const val LOG_FILE = "plugin_audit.jsonl"
    private const val MAX_LOG_BYTES = 2L * 1024 * 1024
    private val gson = Gson()

    data class AuditEvent(
        val timestamp: String,
        val pluginId: String,
        val event: String,
        val detail: String? = null
    )

    @Synchronized
    fun log(context: Context, pluginId: String, event: String, detail: String? = null) {
        try {
            val dir = File(context.applicationContext.filesDir, LOG_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, LOG_FILE)
            rotateIfNeeded(file)
            val entry = AuditEvent(IsoTimestamp.format(Date()), pluginId, event, detail)
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

    /** Returns the most recent audit lines, newest last, for display in a future UI. */
    fun readRecent(context: Context, maxLines: Int = 200): List<String> {
        return try {
            val file = File(File(context.applicationContext.filesDir, LOG_DIR), LOG_FILE)
            if (!file.exists()) emptyList() else file.readLines().takeLast(maxLines)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
