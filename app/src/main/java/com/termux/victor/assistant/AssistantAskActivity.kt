package com.termux.victor.assistant

import android.app.Activity
import android.os.Bundle
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * Not exported. Shows the fleet confirm dialog for a `??` command and writes the decision
 * to a result file under the Termux prefix. It never runs the command itself, and it does
 * not write the query or command to logcat.
 */
class AssistantAskActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent?.extras
        if (extras == null) {
            finish()
            return
        }
        val resultPath = extras.getString(EXTRA_RESULT)
        val result = safeResultFile(resultPath)
        if (result == null) {
            finish()
            return
        }
        val command = extras.getString(EXTRA_COMMAND).orEmpty()
        val query = extras.getString(EXTRA_QUERY).orEmpty()
        val tableDir = AssistantBootstrap.tableDir()
        val table = VsAskCli.loadTable(tableDir)
        val proposed = resolveProposed(table, query, command)
        if (proposed == null) {
            writeAtomic(result, "STATUS=CANCEL\nCOMMAND\n")
            finish()
            return
        }
        AssistantConfirmDialog.show(this, proposed) { run ->
            if (run) {
                writeAtomic(result, "STATUS=RUN\nCOMMAND\n${proposed.command}\n")
            } else {
                writeAtomic(result, "STATUS=CANCEL\nCOMMAND\n")
            }
            finish()
        }
    }

    private fun resolveProposed(table: IntentTable?, query: String, command: String): Resolution.Proposed? {
        if (table != null && query.isNotBlank()) {
            val r = AssistantResolver(table).resolve(
                if (AssistantQuery.parse(query) != null) query else "?? $query",
                rootAvailable = true,
            )
            if (r is Resolution.Proposed) return r
        }
        if (command.isBlank()) return null
        val verdict = DestructiveClassifier.classify(command)
        val stub = Intent(id = "direct", title = "Command", keywords = emptyList(), commandUser = command)
        return Resolution.Proposed(stub, command, command.trim().startsWith("su "), verdict)
    }

    private fun safeResultFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        val canonical = try {
            f.canonicalFile
        } catch (_: Exception) {
            return null
        }
        val prefix = File(TermuxConstants.TERMUX_FILES_DIR_PATH).canonicalFile
        val underPrefix = canonical.path == prefix.path || canonical.path.startsWith(prefix.path + "/")
        val underCache = cacheDir?.canonicalFile?.let { canonical.path == it.path || canonical.path.startsWith(it.path + "/") } ?: false
        if (!underPrefix && !underCache) return null
        canonical.parentFile?.mkdirs()
        return canonical
    }

    private fun writeAtomic(dest: File, text: String) {
        val tmp = File(dest.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(dest)) {
            dest.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        const val EXTRA_QUERY = "com.termux.victor.assistant.QUERY"
        const val EXTRA_COMMAND = "com.termux.victor.assistant.COMMAND"
        const val EXTRA_RESULT = "com.termux.victor.assistant.RESULT"
        const val EXTRA_DESTRUCTIVE = "com.termux.victor.assistant.DESTRUCTIVE"
    }
}
