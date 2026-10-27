package com.termux.victor.plugin

import android.content.Context
import java.io.File

/**
 * Resolves the shell command line to run an already-installed plugin. This class
 * deliberately contains no execution, download, or install logic of its own — it only
 * composes a command string and enforces the enable/authorized-use gate, then hands the
 * command to the caller to feed into the same terminal session
 * (SingletonCommunicationUtils.sendTextToTerminal, the same path QEMU/module features
 * already use) so the run stays visible in the user's own shell transcript rather than
 * happening as a hidden background process.
 *
 * It never contacts a network, never writes an entry point to disk, and never runs
 * anything the user has not placed at [PluginManifestEntry.entryPoint] themselves.
 */
object PluginProcessLauncher {

    class PluginNotEnabledException(id: String) : IllegalStateException(
        "Plugin '$id' is not enabled; acknowledge authorized use and enable it before launching"
    )

    class EntryPointMissingException(path: String) : IllegalStateException(
        "Plugin entry point not found at $path — install it yourself first, then try again"
    )

    /**
     * Returns the shell command line for [id], or throws if the plugin is unknown, not
     * enabled, or its entry point isn't present under [pluginInstallDir] yet.
     */
    fun resolveCommand(context: Context, pluginInstallDir: File, id: String): String {
        val record = PluginRegistry.get(context, id)
            ?: throw IllegalArgumentException("Unknown plugin: $id")
        if (record.state != PluginState.ENABLED) {
            throw PluginNotEnabledException(id)
        }
        val entry = record.manifestEntry
        val entryFile = File(pluginInstallDir, entry.entryPoint)
        if (!entryFile.exists()) {
            throw EntryPointMissingException(entryFile.absolutePath)
        }

        PluginRegistry.recordLaunch(context, id)
        PluginLogger.log(context, id, "LAUNCH_RESOLVED", entryFile.absolutePath)

        val quotedDir = shellQuote(pluginInstallDir.absolutePath)
        val quotedEntry = shellQuote("./" + entry.entryPoint)
        val quotedRelEntry = shellQuote(entry.entryPoint)
        val quotedArgs = entry.args.joinToString(" ") { arg -> shellQuote(arg) }
        return "cd $quotedDir && chmod 755 $quotedRelEntry && $quotedEntry $quotedArgs".trim()
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
