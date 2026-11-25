package com.termux.victor.companions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionDetectorTest {

    private class FakeLookup(
        private val installed: Map<String, String>,
        private val sameKey: Set<String> = emptySet(),
    ) : PackageLookup {
        override fun installedVersion(packageId: String): String? = installed[packageId]
        override fun signatureMatchesSelf(packageId: String): Boolean = packageId in sameKey
    }

    @Test
    fun catalogHasTheSevenCompanionsAndNoTasker() {
        val names = CompanionCatalog.ALL.map { it.displayName }
        assertEquals(
            listOf("VictorX11", "VictorGUI", "VictorFloat", "VictorWidget", "VictorBoot", "VictorStyling", "VictorAPI"),
            names,
        )
        assertFalse(names.any { it.contains("Tasker") })
        assertEquals(CompanionCatalog.ALL.size, CompanionCatalog.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun nothingInstalledIsAllOptional() {
        val statuses = CompanionDetector.detectAll(FakeLookup(emptyMap()))
        assertTrue(statuses.all { it.state == CompanionState.NOT_INSTALLED })
        assertTrue(CompanionDetector.report(statuses).lines().all { it.endsWith("not installed (optional)") })
    }

    @Test
    fun sameKeyIsReadyAndOtherKeyIsMismatch() {
        val lookup = FakeLookup(
            mapOf("com.termux.api" to "0.50", "com.termux.boot" to "0.8"),
            sameKey = setOf("com.termux.api"),
        )
        val api = CompanionDetector.detect(CompanionCatalog.byId("api")!!, lookup)
        assertEquals(CompanionState.READY, api.state)
        assertEquals("VictorAPI: ready (com.termux.api 0.50)", CompanionDetector.report(listOf(api)))
        assertEquals(CompanionState.SIGNATURE_MISMATCH, CompanionDetector.detect(CompanionCatalog.byId("boot")!!, lookup).state)
    }

    @Test
    fun x11DoesNotNeedTheSharedUser() {
        val s = CompanionDetector.detect(CompanionCatalog.byId("x11")!!, FakeLookup(mapOf("com.termux.x11" to "")))
        assertEquals(CompanionState.INSTALLED_STANDALONE, s.state)
        assertNull(s.versionName)
    }
}
