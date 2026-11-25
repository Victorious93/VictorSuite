package com.termux.victor.assistant

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import com.termux.victor.util.UUtils
import java.io.File

/**
 * Copies the vs-ask helper, the built-in intent table, and the bashrc hook into the Termux prefix.
 * User overlays in overlays.d/ are never deleted. Safe to call on every app start.
 */
object AssistantInstaller {

    private const val ASSET_HELPER = "runcommand/vs-ask"
    private const val ASSET_INTENTS = "assistant/intents.json"
    private const val ASSET_HOOK = "assistant/bashrc-hook.sh"

    @JvmStatic
    fun install(context: Context) {
        val prefix = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        if (!prefix.isDirectory) return
        try {
            val tableDir = AssistantBootstrap.tableDir(prefix.absolutePath)
            val overlays = AssistantBootstrap.overlaysDir(prefix.absolutePath)
            tableDir.mkdirs()
            overlays.mkdirs()
            AssistantBootstrap.helperFile(prefix.absolutePath).parentFile?.mkdirs()

            val helper = AssistantBootstrap.helperFile(prefix.absolutePath)
            copyAsset(context, ASSET_HELPER, helper)
            UUtils.chmod(helper)

            // Always refresh the built-in table; never touch overlays.d.
            val intents = AssistantBootstrap.intentsFile(prefix.absolutePath)
            copyAsset(context, ASSET_INTENTS, intents)
            if (intents.isFile) {
                try {
                    AssistantBootstrap.writeIndex(IntentTable.parse(intents.readText()), AssistantBootstrap.indexFile(prefix.absolutePath))
                } catch (_: Exception) {
                    // keep any previous index
                }
            }

            val hook = context.assets.open(ASSET_HOOK).bufferedReader().use { it.readText() }
            AssistantBootstrap.installHook(AssistantBootstrap.bashrcFile(prefix.absolutePath), hook)

            AssistantBootstrap.setEnabledFlag(AssistantSettings.isEnabled(context), prefix.absolutePath)
        } catch (_: Exception) {
            // Prefix may be mid-bootstrap; a later start will retry.
        }
    }

    private fun copyAsset(context: Context, name: String, dest: File) {
        dest.parentFile?.mkdirs()
        context.assets.open(name).use { src ->
            dest.outputStream().use { out -> src.copyTo(out) }
        }
    }
}
