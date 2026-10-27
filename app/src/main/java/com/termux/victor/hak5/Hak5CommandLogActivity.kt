package com.termux.zerocore.hak5

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.termux.R

/**
 * Read-only viewer for the persistent Hak5 command log (plan Phase 2 §5).
 * Shows every executed/blocked action with secrets already redacted on disk.
 */
class Hak5CommandLogActivity : AppCompatActivity() {

    private lateinit var logText: TextView
    private lateinit var clearButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hak5_command_log)
        logText = findViewById(R.id.hak5_command_log_text)
        clearButton = findViewById(R.id.hak5_command_log_clear)
        clearButton.setOnClickListener { confirmClear() }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val entries = Hak5CommandLog.read(this, max = 500)
        if (entries.isEmpty()) {
            logText.text = getString(R.string.hak5_command_log_empty)
            return
        }
        // Newest first for readability.
        logText.text = entries.asReversed().joinToString("\n\n") { format(it) }
    }

    private fun format(e: Hak5CommandLog.CommandLogEntry): String {
        val sb = StringBuilder()
        sb.append(e.timestamp).append('\n')
        sb.append(e.kind).append(' ').append(e.target).append(" · ").append(e.action)
        sb.append("  [").append(e.severity).append("]\n")
        when {
            e.blocked -> sb.append("BLOCKED: ").append(e.blockReason ?: "")
            e.executed -> {
                sb.append(if (e.success == true) "OK" else "FAIL")
                e.httpStatus?.let { sb.append(" HTTP ").append(it) }
                e.responseSnippet?.let { sb.append('\n').append(it) }
            }
            else -> sb.append("(no network call)")
        }
        e.params?.let { if (it.isNotBlank()) sb.append("\nparams: ").append(it) }
        return sb.toString()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.hak5_command_log_clear)
            .setMessage(R.string.hak5_command_log_clear)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Hak5CommandLog.clear(this)
                render()
                Toast.makeText(this, R.string.hak5_command_log_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
