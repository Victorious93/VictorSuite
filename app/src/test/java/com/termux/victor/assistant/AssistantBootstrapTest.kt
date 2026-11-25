package com.termux.victor.assistant

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssistantBootstrapTest {

    @get:Rule val tmp = TemporaryFolder()

    private val hook = File("src/main/assets/assistant/bashrc-hook.sh").readText()

    @Test fun hookStartsDisabledOffAndCreatesFlag() {
        val prefix = tmp.newFolder("usr")
        assertTrue(AssistantBootstrap.isEnabledFlag(prefix.absolutePath))
        AssistantBootstrap.setEnabledFlag(false, prefix.absolutePath)
        assertFalse(AssistantBootstrap.isEnabledFlag(prefix.absolutePath))
        assertTrue(AssistantBootstrap.disabledFile(prefix.absolutePath).isFile)
        AssistantBootstrap.setEnabledFlag(true, prefix.absolutePath)
        assertTrue(AssistantBootstrap.isEnabledFlag(prefix.absolutePath))
        assertFalse(AssistantBootstrap.disabledFile(prefix.absolutePath).exists())
    }

    @Test fun installHookCreatesVictorSuiteBlock() {
        val bashrc = tmp.newFile("bash.bashrc")
        bashrc.writeText("PS1='$ '\n")
        assertTrue(AssistantBootstrap.installHook(bashrc, hook))
        val text = bashrc.readText()
        assertTrue(text.contains(AssistantBootstrap.BLOCK_START))
        assertTrue(text.contains(AssistantBootstrap.BLOCK_END))
        assertTrue(text.contains(AssistantBootstrap.HOOK_START))
        assertTrue(text.contains("alias '??'='set -f; __vs_ask'"))
        assertTrue(text.startsWith("PS1="))
    }

    @Test fun installHookIsIdempotentAndPreservesOtherCommands() {
        val bashrc = tmp.newFile("bash.bashrc")
        bashrc.writeText(
            """
            # VictorSuite[Start]
            export VS_EXTRA=1
            # VictorSuite[End]
            """.trimIndent() + "\n",
        )
        assertTrue(AssistantBootstrap.installHook(bashrc, hook))
        assertTrue(AssistantBootstrap.installHook(bashrc, hook))
        val text = bashrc.readText()
        assertEquals(1, Regex(Regex.escape(AssistantBootstrap.HOOK_START)).findAll(text).count())
        assertTrue(text.contains("export VS_EXTRA=1"))
        val start = text.indexOf(AssistantBootstrap.BLOCK_START)
        val end = text.indexOf(AssistantBootstrap.BLOCK_END)
        val hookAt = text.indexOf(AssistantBootstrap.HOOK_START)
        assertTrue(start < hookAt && hookAt < end)
    }

    @Test fun mergeHookReplacesPreviousHookBody() {
        val first = AssistantBootstrap.mergeHook("", "echo first")
        val second = AssistantBootstrap.mergeHook(first, "echo second")
        assertTrue(second.contains("echo second"))
        assertFalse(second.contains("echo first"))
    }

    @Test fun writeIndexIsTabSeparatedOneLinePerIntent() {
        val json = File("src/main/assets/assistant/intents.json").readText()
        val table = IntentTable.parse(json)
        val text = AssistantBootstrap.indexText(table)
        assertTrue(text.lines().any { it.startsWith("cpu_temp\t") })
        assertFalse(text.contains("\t\t\t")) // no accidental empty-field blowups required, just parsable
        table.intents.forEach { intent ->
            assertTrue(intent.id, text.lines().any { it.startsWith(intent.id + "\t") && !it.contains("\n", false) })
        }
    }

    @Test fun vsAskCliFormatsProposedAndNoMatch() {
        val dir = tmp.newFolder("assistant")
        File("src/main/assets/assistant/intents.json").copyTo(File(dir, "intents.json"))
        val proposed = VsAskCli.resolve(dir, "cpu temp", rootAvailable = false) as Resolution.Proposed
        val rec = VsAskCli.format(proposed)
        assertTrue(rec.startsWith("STATUS=PROPOSED"))
        assertTrue(rec.contains("ID=cpu_temp"))
        assertTrue(rec.contains("DESTRUCTIVE=0"))
        assertTrue(rec.substringAfter("COMMAND\n").contains("dumpsys thermalservice"))

        val none = VsAskCli.format(VsAskCli.resolve(dir, "make me a sandwich", false))
        assertTrue(none.startsWith("STATUS=NO_MATCH"))
    }

    @Test fun vsAskCliAppliesOverlaysById() {
        val dir = tmp.newFolder("assistant")
        File("src/main/assets/assistant/intents.json").copyTo(File(dir, "intents.json"))
        File(dir, "overlays.d").mkdirs()
        File(dir, "overlays.d/z-uptime.json").writeText(
            """{"intents":[{"id":"uptime","title":"Uptime overlay","keywords":["overlay uptime"],"command_user":"echo overlay"}]}""",
        )
        val r = VsAskCli.resolve(dir, "overlay uptime", false) as Resolution.Proposed
        assertEquals("uptime", r.intent.id)
        assertEquals("echo overlay", r.command)
    }
}
