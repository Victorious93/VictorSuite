package com.termux.victor.assistant

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantWiringTest {
    private fun text(path: String) = File(path).readText()

    @Test fun settingsToggleIsWired() {
        val layout = text("src/main/res/layout/activity_victor_suite_settings.xml")
        assertTrue(layout.contains("android:id=\"@+id/assistant_switch\""))
        assertTrue(layout.contains("@string/vs_assistant_settings_title"))
        val activity = text("src/main/java/com/termux/victor/settings/VictorSuiteSettingsActivity.kt")
        assertTrue(activity.contains("AssistantSettings"))
        assertTrue(activity.contains("assistantSwitch"))
        val strings = text("src/main/res/values/strings.xml")
        assertTrue(strings.contains("name=\"vs_assistant_settings_title\""))
        assertTrue(strings.contains("Terminal assistant (??)"))
    }

    @Test fun confirmActivityIsNotExportedAndUsesNeonDialog() {
        val manifest = text("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains(".victor.assistant.AssistantAskActivity"))
        val block = manifest.substring(manifest.indexOf("AssistantAskActivity"))
        val snippet = block.substring(0, block.indexOf("/>") + 2)
        assertTrue(snippet.contains("android:exported=\"false\""))
        val dialog = text("src/main/java/com/termux/victor/assistant/AssistantConfirmDialog.kt")
        assertTrue(dialog.contains("vs_neon_bg"))
        assertTrue(dialog.contains("vs_neon_cyan"))
        assertTrue(dialog.contains("vs_neon_magenta"))
        val colors = text("src/main/res/values/colors.xml")
        assertTrue(colors.contains("<color name=\"vs_neon_bg\">#FF0B0F1A</color>"))
        assertTrue(colors.contains("<color name=\"vs_neon_cyan\">#FF00E5FF</color>"))
        assertTrue(colors.contains("<color name=\"vs_neon_magenta\">#FFFF2BD6</color>"))
    }

    @Test fun hookAndHelperAreInstalledFromTermuxActivity() {
        val activity = text("src/main/java/com/termux/app/TermuxActivity.java")
        assertTrue(activity.contains("AssistantInstaller.install"))
        val hook = text("src/main/assets/assistant/bashrc-hook.sh")
        assertTrue(hook.contains("alias '??'='set -f; __vs_ask'"))
        assertTrue(hook.contains("[[ \$- == *i* ]]"))
        val helper = text("src/main/assets/runcommand/vs-ask")
        assertTrue(helper.contains("The optional LLM is not wired"))
        assertFalse(helper.contains("http://") || helper.contains("https://"))
    }
}
