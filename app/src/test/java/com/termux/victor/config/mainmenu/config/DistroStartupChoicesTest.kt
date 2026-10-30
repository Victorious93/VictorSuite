package com.termux.victor.config.mainmenu.config

import org.junit.Assert.assertEquals
import org.junit.Test

class DistroStartupChoicesTest {
    @Test
    fun startupScreenOffersEveryBundledDistro() {
        val keys = DistroLoginClickConfig.startupChoices().map { it.key }
        assertEquals(listOf("ubuntu", "kali", "parrot", "blackarch"), keys)
    }

    @Test
    fun startupChoicesMatchKeysAcceptedByDistroLoginScript() {
        val script = java.io.File("../scripts/distro-login.sh").let { if (it.exists()) it else java.io.File("scripts/distro-login.sh") }
        val text = script.readText()
        for (c in DistroLoginClickConfig.startupChoices()) {
            assert(text.contains("distro-login.sh ${c.key}")) { "script does not document key ${c.key}" }
        }
    }
}
