package com.termux.victor.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FleetThemeRoleMapTest {

    private val full = FleetPalette(
        background = 1, surface = 2, primary = 3, secondary = 4, onBackground = 5,
        muted = 6, danger = 7, stale = 8, tile = 9,
    )

    @Test fun paletteKeysMapToSpecRoles() {
        assertEquals(1, full.colorFor(FleetRole.BACKGROUND))
        assertEquals(1, full.colorFor(FleetRole.ON_PRIMARY))
        assertEquals(1, full.colorFor(FleetRole.ON_SECONDARY))
        assertEquals(1, full.colorFor(FleetRole.ON_ERROR))
        assertEquals(2, full.colorFor(FleetRole.SURFACE))
        assertEquals(3, full.colorFor(FleetRole.PRIMARY))
        assertEquals(4, full.colorFor(FleetRole.SECONDARY))
        assertEquals(5, full.colorFor(FleetRole.ON_BACKGROUND))
        assertEquals(5, full.colorFor(FleetRole.ON_SURFACE))
        assertEquals(6, full.colorFor(FleetRole.ON_SURFACE_VARIANT))
        assertEquals(6, full.colorFor(FleetRole.OUTLINE))
        assertEquals(7, full.colorFor(FleetRole.ERROR))
        assertEquals(9, full.colorFor(FleetRole.SURFACE_VARIANT))
        assertEquals(8, full.colorFor(FleetRole.STALE_ACCENT))
    }

    @Test fun builtInColoursClassify() {
        assertEquals(FleetRole.BACKGROUND, FleetThemeRoleMap.backgroundRole(0xFF2B2B2B.toInt()))
        assertEquals(FleetRole.SURFACE_VARIANT, FleetThemeRoleMap.backgroundRole(0x55000000))
        assertNull(FleetThemeRoleMap.backgroundRole(0xFF000000.toInt()))
        assertEquals(FleetRole.ON_BACKGROUND, FleetThemeRoleMap.textRole(0xFFFFFFFF.toInt()))
        assertEquals(FleetRole.PRIMARY, FleetThemeRoleMap.textRole(0xFF48BAF3.toInt()))
        assertEquals(FleetRole.ON_SURFACE_VARIANT, FleetThemeRoleMap.textRole(0xFF9E9E9E.toInt()))
        // Session-list red for a failed session is not chrome: left alone.
        assertNull(FleetThemeRoleMap.textRole(0xFFFF0000.toInt()))
    }

    @Test fun missingKeyKeepsOriginalAndNeverSubstitutes() {
        val partial = FleetPalette(background = 0x11223344)
        val white = 0xFFFFFFFF.toInt()
        assertEquals(0x11223344, FleetThemeRoleMap.resolve(partial, FleetRole.BACKGROUND, 0xFF2B2B2B.toInt()))
        assertEquals(white, FleetThemeRoleMap.resolve(partial, FleetRole.ON_BACKGROUND, white))
        assertEquals(white, FleetThemeRoleMap.resolve(null, FleetRole.ON_BACKGROUND, white))
        assertEquals(white, FleetThemeRoleMap.resolve(full, null, white))
    }
}
