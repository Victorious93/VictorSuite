package com.termux.victor.assistant

import java.io.File

/**
 * Command-line entry for [AssistantResolver], used by vs-ask via app_process.
 * No logging, no network. Output is a VSASK record consumed by the shell helper.
 */
object VsAskCli {

    const val DEFAULT_TABLE_DIR = "/data/data/com.termux/files/usr/share/victorsuite/assistant"

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(System.getenv("VS_ASK_TABLE_DIR") ?: DEFAULT_TABLE_DIR)
        val root = parseRoot(System.getenv("VS_ASK_ROOT")) ?: suPresent()
        val query = args.joinToString(" ").trim()
        System.out.print(format(resolve(dir, query, root)))
    }

    fun resolve(tableDir: File, query: String, rootAvailable: Boolean): Resolution {
        val table = loadTable(tableDir) ?: return Resolution.NoMatch(emptyList())
        val line = if (AssistantQuery.parse(query) != null) query else "?? $query"
        return AssistantResolver(table).resolve(line, rootAvailable)
    }

    fun loadTable(tableDir: File): IntentTable? {
        val base = File(tableDir, "intents.json")
        if (!base.isFile) return null
        var table = try {
            IntentTable.parse(base.readText())
        } catch (_: Exception) {
            return null
        }
        val overlays = File(tableDir, "overlays.d")
        if (overlays.isDirectory) {
            overlays.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.sortedBy { it.name }
                ?.forEach { f ->
                    try {
                        table = table.withOverlay(f.readText())
                    } catch (_: Exception) {
                        // malformed overlays are ignored
                    }
                }
        }
        return table
    }

    fun format(r: Resolution): String = when (r) {
        is Resolution.Proposed -> buildString {
            appendLine("STATUS=PROPOSED")
            appendLine("DESTRUCTIVE=" + if (r.verdict.destructive) "1" else "0")
            appendLine("USES_ROOT=" + if (r.usesRoot) "1" else "0")
            appendLine("ID=" + r.intent.id)
            appendLine("TITLE=" + oneLine(r.intent.title))
            appendLine("EXPLAIN=" + oneLine(r.intent.explain))
            appendLine("NOTE=" + oneLine(r.intent.androidNote))
            r.verdict.reasons.forEach { appendLine("REASON=" + oneLine(it)) }
            appendLine("COMMAND")
            appendLine(r.command)
        }
        is Resolution.NeedsRoot -> buildString {
            appendLine("STATUS=NEEDS_ROOT")
            appendLine("ID=" + r.intent.id)
            appendLine("TITLE=" + oneLine(r.intent.title))
            appendLine("EXPLAIN=" + oneLine(r.intent.explain))
            appendLine("NOTE=" + oneLine(r.intent.androidNote))
            appendLine("COMMAND")
            appendLine(r.command)
        }
        is Resolution.NoMatch -> buildString {
            appendLine("STATUS=NO_MATCH")
            r.suggestions.forEach { appendLine("SUGGEST=" + it.id + "|" + oneLine(it.title)) }
            appendLine("COMMAND")
        }
        is Resolution.NotAQuery -> buildString {
            appendLine("STATUS=NO_MATCH")
            appendLine("COMMAND")
        }
    }

    private fun oneLine(s: String) = s.replace("\r", " ").replace("\n", " ")

    private fun parseRoot(v: String?): Boolean? = when (v?.lowercase()) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> null
    }

    private fun suPresent(): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su")
        if (paths.any { File(it).exists() }) return true
        val path = System.getenv("PATH") ?: return false
        return path.split(':').any { File(it, "su").exists() }
    }
}
