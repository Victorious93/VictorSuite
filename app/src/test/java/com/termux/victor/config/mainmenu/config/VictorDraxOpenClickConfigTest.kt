package com.termux.victor.config.mainmenu.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VictorDraxOpenClickConfigTest {
    private fun file(path: String) = File("../$path").let { if (it.exists()) it else File(path) }

    @Test
    fun prefersLauncherIntent() {
        assertEquals("launcher", VictorDraxOpenClickConfig.pickLaunchIntent("launcher", "explicit") { true })
    }

    @Test
    fun fallsBackToExplicitComponentOnlyWhenItResolves() {
        assertEquals("explicit", VictorDraxOpenClickConfig.pickLaunchIntent(null, "explicit") { true })
        assertNull(VictorDraxOpenClickConfig.pickLaunchIntent(null, "explicit") { false })
    }

    @Test
    fun contractConstants() {
        assertEquals("com.victorious.victordrax", VictorDraxOpenClickConfig.PACKAGE)
        assertEquals("com.victorious.victordrax.MainActivity", VictorDraxOpenClickConfig.LAUNCHER_ACTIVITY)
        assertTrue(VictorDraxOpenClickConfig.RELEASES_URL.startsWith("https://github.com/Victorious93/victordrax"))
    }

    @Test
    fun drawerMenusAndManifestDeclareVictorDrax() {
        for (lang in listOf("en", "cn")) {
            val xml = file("app/src/main/assets/mainmenu/$lang/zt_menu_config.xml").readText()
            assertTrue(lang, xml.contains("java:com.termux.victor.config.mainmenu.config.VictorDraxOpenClickConfig"))
        }
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("<package android:name=\"com.victorious.victordrax\" />"))
    }
}
