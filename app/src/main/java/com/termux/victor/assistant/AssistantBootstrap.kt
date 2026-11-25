package com.termux.victor.assistant

import java.io.File

/**
 * Idempotent, Android-free install of the `??` bashrc hook and the on/off flag file.
 * The helper itself is copied by [AssistantInstaller].
 */
object AssistantBootstrap {
    const val BLOCK_START = "# VictorSuite[Start]"
    const val BLOCK_END = "# VictorSuite[End]"
    const val HOOK_START = "# vs-ask[start]"
    const val HOOK_END = "# vs-ask[end]"

    const val DEFAULT_PREFIX = "/data/data/com.termux/files/usr"

    fun tableDir(prefix: String = DEFAULT_PREFIX) = File(prefix, "share/victorsuite/assistant")
    fun overlaysDir(prefix: String = DEFAULT_PREFIX) = File(tableDir(prefix), "overlays.d")
    fun intentsFile(prefix: String = DEFAULT_PREFIX) = File(tableDir(prefix), "intents.json")
    fun indexFile(prefix: String = DEFAULT_PREFIX) = File(tableDir(prefix), "intents.idx")
    fun disabledFile(prefix: String = DEFAULT_PREFIX) = File(tableDir(prefix), "disabled")
    fun helperFile(prefix: String = DEFAULT_PREFIX) = File(prefix, "libexec/victorsuite/vs-ask")
    fun bashrcFile(prefix: String = DEFAULT_PREFIX) = File(prefix, "etc/bash.bashrc")

    /** When [enabled] is false a flag file is created; vs-ask refuses to run while it exists. Default is on (no file). */
    fun setEnabledFlag(enabled: Boolean, prefix: String = DEFAULT_PREFIX) {
        val f = disabledFile(prefix)
        if (enabled) {
            if (f.exists()) f.delete()
        } else {
            f.parentFile?.mkdirs()
            f.writeText("")
        }
    }

    fun isEnabledFlag(prefix: String = DEFAULT_PREFIX): Boolean = !disabledFile(prefix).exists()

    /**
     * Inserts or replaces the vs-ask hook inside the VictorSuite bashrc block.
     * Other lines already in that block are left alone. Creates the block if missing.
     * Returns false only when [bashrc] cannot be created or written.
     */
    fun installHook(bashrc: File, hookBody: String): Boolean {
        return try {
            val existing = if (bashrc.exists()) bashrc.readText() else ""
            bashrc.parentFile?.mkdirs()
            bashrc.writeText(mergeHook(existing, hookBody))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun mergeHook(existing: String, hookBody: String): String {
        val hook = wrappedHook(hookBody)
        val lines = existing.replace("\r\n", "\n").split("\n").toMutableList()
        // Drop a trailing empty line added by split on a final newline so we can re-add one later.
        if (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.size - 1)

        val hookStart = lines.indexOfFirst { it.trim() == HOOK_START }
        val hookEnd = lines.indexOfFirst { it.trim() == HOOK_END }
        if (hookStart >= 0 && hookEnd > hookStart) {
            val newLines = lines.take(hookStart) + hook.lines() + lines.drop(hookEnd + 1)
            return join(newLines)
        }

        val blockEnd = lines.indexOfFirst { it.contains("VictorSuite[End]") }
        if (blockEnd >= 0) {
            val newLines = lines.take(blockEnd) + hook.lines() + lines.drop(blockEnd)
            return join(newLines)
        }

        val blockStart = lines.indexOfFirst { it.contains("VictorSuite[Start]") }
        return if (blockStart >= 0) {
            join(lines + hook.lines() + listOf(BLOCK_END))
        } else {
            join(lines + listOf(BLOCK_START) + hook.lines() + listOf(BLOCK_END))
        }
    }

    fun wrappedHook(hookBody: String): String =
        (listOf(HOOK_START) + hookBody.replace("\r\n", "\n").trimEnd().lines() + listOf(HOOK_END)).joinToString("\n")

    /**
     * Compact tab-separated index used by vs-ask when app_process cannot run the Kotlin matcher.
     * Tabs and newlines inside fields are flattened so each intent is one line.
     */
    fun writeIndex(table: IntentTable, dest: File) {
        dest.parentFile?.mkdirs()
        dest.writeText(indexText(table))
    }

    fun indexText(table: IntentTable): String = buildString {
        for (i in table.intents) {
            val keys = (i.keywords + i.aliases).joinToString("|")
            append(listOf(i.id, i.title, keys, i.commandUser.orEmpty(), i.commandRoot.orEmpty(), i.explain, i.androidNote)
                .joinToString("\t") { flat(it) })
            append('\n')
        }
    }

    private fun flat(s: String) = s.replace("\t", " ").replace("\r", " ").replace("\n", " ")

    private fun join(lines: List<String>): String {
        val body = lines.joinToString("\n")
        return if (body.endsWith("\n")) body else body + "\n"
    }
}
