package com.termux.victor.config.mainmenu.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndraxOpenClickConfigTest {
    private fun file(path: String) = File("../$path").let { if (it.exists()) it else File(path) }

    @Test
    fun prefersLauncherIntent() {
        assertEquals("launcher", AndraxOpenClickConfig.pickLaunchIntent("launcher", "explicit") { true })
    }

    @Test
    fun fallsBackToExplicitComponentOnlyWhenItResolves() {
        assertEquals("explicit", AndraxOpenClickConfig.pickLaunchIntent(null, "explicit") { true })
        assertNull(AndraxOpenClickConfig.pickLaunchIntent(null, "explicit") { false })
    }

    @Test
    fun contractConstants() {
        assertEquals("com.victorious.andrax", AndraxOpenClickConfig.PACKAGE)
        assertEquals("com.victorious.andrax.MainActivity", AndraxOpenClickConfig.LAUNCHER_ACTIVITY)
        assertTrue(AndraxOpenClickConfig.RELEASES_URL.startsWith("https://github.com/Victorious93/victorious-andrax"))
    }

    @Test
    fun drawerMenusAndManifestDeclareAndrax() {
        for (lang in listOf("en", "cn")) {
            val xml = file("app/src/main/assets/mainmenu/$lang/zt_menu_config.xml").readText()
            assertTrue(lang, xml.contains("java:com.termux.victor.config.mainmenu.config.AndraxOpenClickConfig"))
        }
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("<package android:name=\"com.victorious.andrax\" />"))
    }
}
