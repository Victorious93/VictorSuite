package com.termux.victor.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level checks for the My PC feature: the warning text, the menu and manifest wiring, the
 * dark theme, and that the existing in-app scrcpy client (phone views a remote device) is left alone.
 */
class MyPcWiringTest {
    private fun file(path: String) = File("../$path").let { if (it.exists()) it else File(path) }
    private fun text(path: String) = file(path).readText()

    private fun stringValue(name: String): String {
        val xml = text("app/src/main/res/values/strings.xml")
        val match = Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(xml)
        assertTrue("string $name missing", match != null)
        return match!!.groupValues[1]
    }

    private val myPcSources: List<File>
        get() = file("app/src/main/java/com/termux/victor/pc").listFiles { f -> f.extension == "kt" }!!.toList()

    @Test
    fun warningCoversEverythingTheBriefRequires() {
        val warning = stringValue("my_pc_warning_body").lowercase()
        assertTrue(warning.contains("screen access"))
        assertTrue(warning.contains("input control"))
        assertTrue(warning.contains("adb is powerful"))
        assertTrue(warning.contains("trusted lan"))
        assertTrue(warning.contains("tailscale"))
        assertTrue(warning.contains("never expose adb to the internet"))
        assertTrue(stringValue("my_pc_warning_title").lowercase().contains("control"))
    }

    @Test
    fun setupActionsAreGatedByTheWarning() {
        val activity = text("app/src/main/java/com/termux/victor/pc/MyPcActivity.kt")
        assertTrue(activity.contains("openDevOptions.isEnabled = ack"))
        assertTrue(activity.contains("buildButton.isEnabled = ack"))
        assertTrue(activity.contains("MyPcAckGate.isAcknowledged(this)"))
        // Stopping must stay available even when the gate is locked.
        assertTrue(activity.contains("stopDevOptions.setOnClickListener { openDeveloperOptions() }"))
        assertFalse(activity.contains("stopDevOptions.isEnabled"))
    }

    @Test
    fun drawerMenusMenuClassAndManifestAreWired() {
        for (lang in listOf("en", "cn")) {
            val xml = text("app/src/main/assets/mainmenu/$lang/zt_menu_config.xml")
            assertTrue(lang, xml.contains("java:com.termux.victor.config.mainmenu.config.MyPcClickConfig"))
        }
        assertTrue(text("app/src/main/java/com/termux/victor/config/mainmenu/MainMenuConfig.java")
            .contains("new MyPcClickConfig()"))
        val manifest = text("app/src/main/AndroidManifest.xml")
        val declaration = Regex("<activity\\s+android:name=\"\\.victor\\.pc\\.MyPcActivity\"[^>]*/>", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)
        assertTrue("MyPcActivity not declared", declaration != null)
        assertTrue(declaration!!.value.contains("android:exported=\"false\""))
    }

    @Test
    fun existingInAppScrcpyClientIsUntouched() {
        val manifest = text("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:name=\".victor.scrcpy.MainActivity\""))
        val remote = text("app/src/main/java/com/termux/victor/config/mainmenu/config/RemoteConnectionClickConfig.kt")
        assertTrue(remote.contains("import com.termux.victor.scrcpy.MainActivity"))
        assertTrue(remote.contains("Intent(mContext, MainActivity::class.java)"))
        assertFalse(remote.contains("MyPc"))
        for (lang in listOf("en", "cn")) {
            val xml = text("app/src/main/assets/mainmenu/$lang/zt_menu_config.xml")
            assertTrue(lang, xml.contains("java:com.termux.victor.config.mainmenu.config.RemoteConnectionClickConfig"))
        }
        assertEquals("ADB label server", stringValue("remote_connection"))
    }

    @Test
    fun myPcDoesNotReuseTheViewerClientClassesOrPorts() {
        assertTrue(myPcSources.isNotEmpty())
        for (source in myPcSources) {
            val code = source.readText()
            // Kdoc may mention the viewer client by name; code must not import or call it.
            val importsOrCalls = code.lines().filter { !it.trim().startsWith("*") && !it.trim().startsWith("//") }
            for (line in importsOrCalls) {
                assertFalse("${source.name}: $line", line.contains("com.termux.victor.scrcpy") && line.contains("import"))
                assertFalse("${source.name}: $line", line.contains("SendCommands") || line.contains("Scrcpy("))
                assertFalse("${source.name}: $line", Regex("\\b(7007|5555)\\b").containsMatchIn(line))
            }
        }
    }

    @Test
    fun myPcOpensNoListenerAndNoNetworkConnections() {
        for (source in myPcSources) {
            val code = source.readText()
            assertFalse(source.name, code.contains("ServerSocket"))
            assertFalse(source.name, code.contains("java.net.Socket"))
            assertFalse(source.name, code.contains("HttpURLConnection"))
            assertFalse(source.name, code.contains("setprop"))
            assertFalse(source.name, code.contains("WRITE_SECURE_SETTINGS"))
        }
    }

    @Test
    fun layoutIsDarkNeonWithoutWhiteBackgrounds() {
        val layout = text("app/src/main/res/layout/activity_my_pc.xml")
        assertTrue(layout.contains("@color/vs_neon_bg"))
        assertFalse(layout.lowercase().contains("#ffffff"))
        assertFalse(Regex("android:background=\"@color/(white|color_ffffff|color_FFFFFF|main)\"").containsMatchIn(layout))
        val colors = text("app/src/main/res/values/colors.xml")
        assertTrue(colors.contains("<color name=\"vs_neon_bg\">#FF0B0F1A</color>"))
        assertTrue(colors.contains("<color name=\"vs_neon_cyan\">#FF00E5FF</color>"))
        assertTrue(colors.contains("<color name=\"vs_neon_magenta\">#FFFF2BD6</color>"))
        for (drawable in listOf("bg_my_pc_button", "bg_my_pc_card", "bg_my_pc_warning", "bg_my_pc_input")) {
            val xml = text("app/src/main/res/drawable/$drawable.xml").lowercase()
            assertFalse(drawable, xml.contains("#ffffff") || xml.contains("@color/white"))
        }
        assertTrue(text("app/src/main/res/drawable/bg_my_pc_warning.xml").contains("@color/vs_neon_magenta"))
    }

    @Test
    fun newTextIsEnglishOnly() {
        val cjk = Regex("[\\u3040-\\u30ff\\u3400-\\u9fff\\uf900-\\ufaff]")
        val strings = text("app/src/main/res/values/strings.xml")
        val myPcStrings = Regex("<string name=\"my_pc_[^\"]*\"[^>]*>.*?</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(strings).map { it.value }.toList()
        assertTrue(myPcStrings.size > 20)
        for (s in myPcStrings) assertFalse(s, cjk.containsMatchIn(s))
        for (source in myPcSources) assertFalse(source.name, cjk.containsMatchIn(source.readText()))
        assertFalse(cjk.containsMatchIn(text("app/src/main/res/layout/activity_my_pc.xml")))
        assertFalse(text("app/src/main/res/values-zh-rCN/strings.xml").contains("name=\"my_pc_"))
    }

    @Test
    fun scrcpyIsCreditedAsApache2() {
        assertTrue(stringValue("my_pc_credit").contains("Genymobile"))
        assertTrue(stringValue("my_pc_credit").contains("Apache-2.0"))
        val notices = text("THIRD_PARTY_NOTICES.md")
        assertTrue(notices.contains("Genymobile/scrcpy"))
        assertTrue(notices.contains("Apache-2.0"))
        assertTrue(text("docs/MANUAL.md").contains("Genymobile/scrcpy"))
    }

    @Test
    fun pcSetupScriptKeepsScrcpyOptIn() {
        val script = text("pc/victor-pc-setup.sh")
        assertTrue(script.contains("WANT_SCRCPY=0"))
        assertTrue(script.contains("--scrcpy) WANT_SCRCPY=1"))
        assertTrue(script.contains("if [ \"\$WANT_SCRCPY\" != \"1\" ]; then"))
    }
}
