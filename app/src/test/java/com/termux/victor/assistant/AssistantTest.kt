package com.termux.victor.assistant

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTest {

    private val json = File("src/main/assets/assistant/intents.json").readText()
    private val table = IntentTable.parse(json)
    private val resolver = AssistantResolver(table)

    // --- prefix parsing ---
    @Test fun prefixParsing() {
        assertEquals("cpu temp", AssistantQuery.parse("?? cpu temp"))
        assertEquals("cpu", AssistantQuery.parse("??cpu"))
        assertEquals("cpu temp", AssistantQuery.parse("   ??   cpu temp  "))
        assertEquals("", AssistantQuery.parse("??"))
        assertNull(AssistantQuery.parse("echo ??"))
        assertNull(AssistantQuery.parse("ls"))
        assertNull(AssistantQuery.parse("??? what"))
    }

    // --- matcher ---
    private fun top(q: String) = IntentMatcher.confident(IntentMatcher(table).rank(q))?.intent?.id

    @Test fun matchesStarterIntents() {
        assertEquals("cpu_temp", top("cpu temp"))
        assertEquals("cpu_temp", top("what is my CPU temperature?"))
        assertEquals("magisk_modules", top("magisk modules"))
        assertEquals("disk_usage", top("disk space"))
        assertEquals("memory_usage", top("memory usage"))
        assertEquals("battery_status", top("battery status"))
        assertEquals("root_check", top("am i rooted"))
        assertEquals("ip_addresses", top("ip address"))
        assertEquals("processes", top("running processes"))
        assertEquals("android_version", top("android version"))
        assertEquals("kernel", top("kernel version"))
    }

    @Test fun fuzzyTypoStillMatches() {
        assertEquals("magisk_modules", top("magsk modules"))
        assertEquals("cpu_temp", top("cpu temprature"))
    }

    @Test fun lowConfidenceGivesSuggestionsNotACommand() {
        val r = resolver.resolve("?? make me a sandwich", rootAvailable = true)
        assertTrue(r is Resolution.NoMatch)
        val r2 = resolver.resolve("?? magisk", rootAvailable = true)
        assertTrue(r2 !is Resolution.Proposed || r2.intent.id == "magisk_modules")
    }

    @Test fun notAQueryAndBareQuery() {
        assertTrue(resolver.resolve("ls -la", true) is Resolution.NotAQuery)
        assertTrue(resolver.resolve("??", true) is Resolution.NoMatch)
    }

    // --- root vs non-root ---
    @Test fun rootRequiredButAbsent() {
        val r = resolver.resolve("?? magisk modules", rootAvailable = false)
        assertTrue(r is Resolution.NeedsRoot)
        assertTrue((r as Resolution.NeedsRoot).command.contains("/data/adb/modules"))
    }

    @Test fun rootVariantRunsThroughSuAndAlwaysConfirms() {
        val r = resolver.resolve("?? magisk modules", rootAvailable = true) as Resolution.Proposed
        assertTrue(r.usesRoot)
        assertTrue(r.command.startsWith("su -c "))
        assertTrue(r.verdict.destructive)
    }

    @Test fun nonRootVariantPreferredWhenNoRoot() {
        val r = resolver.resolve("?? cpu temp", rootAvailable = false) as Resolution.Proposed
        assertFalse(r.usesRoot)
        assertEquals("dumpsys thermalservice", r.command)
        assertFalse(r.verdict.destructive)
    }

    @Test fun starterUserCommandsAreNotDestructive() {
        for (i in table.intents) i.commandUser?.let { assertFalse(i.id, DestructiveClassifier.classify(it).destructive) }
        for (i in table.intents) i.commandRoot?.let { assertFalse(i.id, DestructiveClassifier.classify(it).destructive) }
    }

    // --- classifier ---
    private fun bad(c: String) = assertTrue(c, DestructiveClassifier.classify(c).destructive)
    private fun ok(c: String) = assertFalse(c, DestructiveClassifier.classify(c).destructive)

    @Test fun classifierPositives() {
        bad("rm -rf /")
        bad("rm file")
        bad("dd if=/dev/zero of=/dev/block/sda")
        bad("mkfs.ext4 /dev/block/x")
        bad("cp a /system/bin/a")
        bad("echo x > /system/etc/hosts")
        bad("mount -o remount,rw /system")
        bad("reboot")
        bad("reboot bootloader")
        bad("su -c 'ls'")
        bad("ls; rm -rf /data")
        bad("ls && reboot")
        bad("echo $(rm x)")
        bad("sh -c 'rm -rf /'")
        bad("pm uninstall com.x")
        bad("pm clear com.x")
        bad("kill -9 -1")
        bad("pkill system_server")
        bad("iptables -F")
        bad("setenforce 0")
        bad("fastboot erase userdata")
        bad("env rm x")
        bad("/system/bin/rm x")
        bad("echo 'unbalanced")
        bad("")
    }

    @Test fun classifierNegatives() {
        ok("ls")
        ok("df -h")
        ok("free -h")
        ok("dumpsys thermalservice")
        ok("dumpsys battery")
        ok("cat /system/build.prop")
        ok("ls /system/bin")
        ok("echo reboot")
        ok("ls /data/adb/modules")
    }

    // --- overlays ---
    @Test fun overlayAddsAndOverridesById() {
        val overlay = """{"intents":[
          {"id":"disk_usage","title":"x","keywords":["disk usage"],"command_user":"df"},
          {"id":"brand_new_overlay","title":"Brand new","keywords":["brand new overlay"],"command_user":"echo overlay"}]}"""
        val t = table.withOverlay(overlay)
        assertEquals("df", t.intents.first { it.id == "disk_usage" }.commandUser)
        assertTrue(t.intents.any { it.id == "brand_new_overlay" })
        assertEquals(table.intents.size + 1, t.intents.size)
    }

    @Test fun overlayCannotWeakenClassifier() {
        val overlay = """{"intents":[{"id":"wipe_all","title":"tidy","keywords":["tidy up"],
          "command_user":"rm -rf /data/local","destructive":false,"safe":true,"endpoint":"https://x"}]}"""
        val r = AssistantResolver(table.withOverlay(overlay)).resolve("?? tidy up", false) as Resolution.Proposed
        assertTrue(r.verdict.destructive)
    }

    @Test fun malformedOverlayIgnored() {
        assertEquals(table.intents, table.withOverlay("{not json").intents)
    }

    // --- privacy: matcher code has no network use ---
    @Test fun assistantSourcesHaveNoNetworkOrLogging() {
        val dir = File("src/main/java/com/termux/victor/assistant")
        val banned = listOf("java.net", "okhttp", "HttpURLConnection", "android.util.Log", "Logger", "println", "Socket")
        dir.listFiles { f -> f.extension == "kt" }!!.forEach { f ->
            val src = f.readText()
            banned.forEach { assertFalse("${f.name} uses $it", src.contains(it)) }
        }
    }
}
