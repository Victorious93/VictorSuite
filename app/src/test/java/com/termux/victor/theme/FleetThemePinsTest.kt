package com.termux.victor.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetThemePinsTest {

    private val current = "B9:9A:AF:E2:C1:41:8B:5D:64:AA:0D:4B:AC:61:FC:F7:A7:BA:81:44:35:E0:EF:89:E8:AA:55:BE:56:4E:4A:69"
    private val revoked = "1B:06:A8:9E:7F:F5:BA:21:C9:A4:F8:F1:F0:4F:95:E5:62:78:A1:22:18:AD:37:33:28:CE:E7:83:1D:4F:BA:CA"
    private val other = "AF:1C:8F:C5:44:9F:58:4A:46:C8:96:CC:C9:7F:35:B0:7B:35:7E:96:EE:66:5F:3A:BF:CB:0D:28:4D:F5:23:55"

    @Test fun pinValuesMatchSpec() {
        assertEquals(listOf(current), FleetThemePins.CURRENT)
        assertEquals(listOf(revoked), FleetThemePins.REVOKED)
        assertTrue("no next cert is published", FleetThemePins.NEXT.isEmpty())
        assertTrue("no release cert exists", FleetThemePins.RELEASE.isEmpty())
    }

    @Test fun contractConstantsMatchSpec() {
        assertEquals("com.victorious.launch", FleetThemeContract.PUBLISHER_PACKAGE)
        assertEquals("com.victorious.launch.theme", FleetThemeContract.AUTHORITY)
        assertEquals("content://com.victorious.launch.theme/current", FleetThemeContract.URI_CURRENT)
        assertEquals("com.victorious.launch.action.FLEET_THEME_CHANGED", FleetThemeContract.ACTION_FLEET_THEME_CHANGED)
        assertEquals("com.victorious.launch.permission.READ_FLEET_THEME", FleetThemeContract.PERMISSION_READ_FLEET_THEME)
        assertEquals(1, FleetThemeContract.SUPPORTED_SCHEMA_VERSION)
    }

    @Test fun acceptsCurrentCertInAnyFormatting() {
        assertTrue(FleetThemePins.isTrustedPublisher("com.victorious.launch", listOf(current)))
        assertTrue(FleetThemePins.isTrustedPublisher("com.victorious.launch", listOf(current.replace(":", "").lowercase())))
    }

    @Test fun rejectsRevokedUnknownAndMalformedCerts() {
        assertFalse(FleetThemePins.isTrustedPublisher("com.victorious.launch", listOf(revoked)))
        assertFalse(FleetThemePins.isTrustedPublisher("com.victorious.launch", listOf(other)))
        assertFalse(FleetThemePins.isTrustedPublisher("com.victorious.launch", listOf("")))
        assertFalse(FleetThemePins.isTrustedPublisher("com.victorious.launch", listOf("TBD")))
        assertFalse(FleetThemePins.isTrustedPublisher("com.victorious.launch", emptyList()))
    }

    @Test fun rejectsWrongPackageEvenWithPinnedCert() {
        assertFalse(FleetThemePins.isTrustedPublisher("com.victorious.launch.debug", listOf(current)))
        assertFalse(FleetThemePins.isTrustedPublisher("com.evil.launch", listOf(current)))
        assertFalse(FleetThemePins.isTrustedPublisher(null, listOf(current)))
    }

    @Test fun rejectsMultipleSigners() {
        assertFalse(FleetThemePins.isTrustedPublisher("com.victorious.launch", listOf(current, other)))
        assertFalse(FleetThemePins.isTrustedPublisher("com.victorious.launch", listOf(current, current)))
    }

    @Test fun normalizeAndDigest() {
        assertNull(FleetThemePins.normalize("AB:CD"))
        assertNull(FleetThemePins.normalize("ZZ".repeat(32)))
        assertEquals(current.replace(":", ""), FleetThemePins.normalize(current))
        // SHA-256("abc")
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            FleetThemePins.sha256Hex("abc".toByteArray()),
        )
    }
}
