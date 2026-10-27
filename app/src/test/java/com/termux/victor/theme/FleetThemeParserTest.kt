package com.termux.victor.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetThemeParserTest {

    private val specJson = """
        {
          "schemaVersion": 1,
          "mode": "NEON_FLEET",
          "iconStyle": "SYSTEM",
          "iconPackPackage": "",
          "updatedAtMs": 0,
          "palette": {
            "background": "#FF0B0C12",
            "surface": "#FF161821",
            "primary": "#FF00E5FF",
            "secondary": "#FFE840FF",
            "onBackground": "#FFF5F7FF",
            "muted": "#FF9AA3B5",
            "danger": "#FFFF6B8A",
            "stale": "#FFFFC14D",
            "tile": "#CC1A1D2B"
          }
        }
    """.trimIndent()

    private fun ok(row: Map<String, Any?>): FleetThemeSnapshot {
        val r = FleetThemeParser.parse(row)
        assertTrue("expected Ok, got $r", r is FleetThemeParseResult.Ok)
        return (r as FleetThemeParseResult.Ok).snapshot
    }

    private fun rejected(row: Map<String, Any?>) {
        val r = FleetThemeParser.parse(row)
        assertTrue("expected Rejected, got $r", r is FleetThemeParseResult.Rejected)
    }

    @Test fun parsesSpecExampleFromJsonColumn() {
        val s = ok(mapOf("schemaVersion" to 1L, "json" to specJson))
        assertEquals(1, s.schemaVersion)
        assertEquals("NEON_FLEET", s.mode)
        assertEquals("SYSTEM", s.iconStyle)
        assertEquals("", s.iconPackPackage)
        assertEquals(0L, s.updatedAtMs)
        assertEquals(0xFF0B0C12.toInt(), s.palette.background)
        assertEquals(0xFF161821.toInt(), s.palette.surface)
        assertEquals(0xFF00E5FF.toInt(), s.palette.primary)
        assertEquals(0xFFE840FF.toInt(), s.palette.secondary)
        assertEquals(0xFFF5F7FF.toInt(), s.palette.onBackground)
        assertEquals(0xFF9AA3B5.toInt(), s.palette.muted)
        assertEquals(0xFFFF6B8A.toInt(), s.palette.danger)
        assertEquals(0xFFFFC14D.toInt(), s.palette.stale)
        assertEquals(0xCC1A1D2B.toInt(), s.palette.tile)
    }

    @Test fun parsesColumnsOnlyRow() {
        val s = ok(mapOf(
            "schemaVersion" to 1L, "mode" to "DARK", "iconStyle" to "TINTED", "iconPackPackage" to "",
            "updatedAtMs" to 1234L, "background" to "#101010", "primary" to "#FF00E5FF",
        ))
        assertEquals("DARK", s.mode)
        assertEquals(1234L, s.updatedAtMs)
        assertEquals(0xFF101010.toInt(), s.palette.background)
        assertEquals(0xFF00E5FF.toInt(), s.palette.primary)
        assertNull(s.palette.surface)
    }

    @Test fun rejectsColumnSchemaVersionNotOne() {
        rejected(mapOf("schemaVersion" to 2L, "json" to specJson))
        rejected(mapOf("schemaVersion" to 0L))
        rejected(mapOf("schemaVersion" to "2"))
        rejected(mapOf("schemaVersion" to 1.5))
    }

    @Test fun rejectsJsonSchemaVersionNotOne() {
        rejected(mapOf("schemaVersion" to 1L, "json" to specJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")))
        rejected(mapOf("json" to specJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"")))
        rejected(mapOf("json" to specJson.replace("\"schemaVersion\": 1,", "")))
    }

    @Test fun rejectsMissingSchemaVersionEverywhere() {
        rejected(mapOf("mode" to "DARK", "background" to "#FF000000"))
        rejected(emptyMap())
    }

    @Test fun rejectsMalformedJson() {
        rejected(mapOf("schemaVersion" to 1L, "json" to "{not json"))
        rejected(mapOf("schemaVersion" to 1L, "json" to "[1,2]"))
        rejected(mapOf("schemaVersion" to 1L, "json" to "$specJson trailing"))
        rejected(mapOf("schemaVersion" to 1L, "json" to "{schemaVersion: 1}")) // lenient-only syntax
        rejected(mapOf("schemaVersion" to 1L, "json" to 42L))
    }

    @Test fun rejectsWrongTypes() {
        rejected(mapOf("json" to specJson.replace("\"updatedAtMs\": 0", "\"updatedAtMs\": \"soon\"")))
        rejected(mapOf("json" to specJson.replace("\"mode\": \"NEON_FLEET\"", "\"mode\": 3")))
        rejected(mapOf("json" to """{"schemaVersion":1,"palette":"#FF000000"}"""))
    }

    @Test fun missingOrInvalidPaletteKeysKeepDefaultsPerKey() {
        val json = """
            {"schemaVersion":1,"mode":"LIGHT","palette":{
              "background":"#FFFFFFFF","surface":"","primary":"cyan","secondary":"#12345",
              "onBackground":" #FF000000","danger":"#GG0000"}}
        """.trimIndent()
        val p = ok(mapOf("json" to json)).palette
        assertEquals(0xFFFFFFFF.toInt(), p.background)
        assertNull(p.surface)
        assertNull(p.primary)
        assertNull(p.secondary)
        assertNull(p.onBackground)
        assertNull(p.danger)
        assertNull(p.muted)
        assertNull(p.tile)
        assertNull(p.stale)
    }

    @Test fun unknownColumnsAndKeysAreIgnored() {
        val json = specJson.replace("\"mode\"", "\"futureField\": {\"x\": [1]}, \"mode\"")
        val s = ok(mapOf("schemaVersion" to 1L, "json" to json, "someNewColumn" to "whatever"))
        assertEquals("NEON_FLEET", s.mode)
    }

    @Test fun jsonPaletteWinsOverColumnsAndColumnFillsGaps() {
        val json = """{"schemaVersion":1,"palette":{"primary":"#FF111111"}}"""
        val p = ok(mapOf("schemaVersion" to 1L, "json" to json, "primary" to "#FF222222", "surface" to "#FF333333")).palette
        assertEquals(0xFF111111.toInt(), p.primary)
        assertEquals(0xFF333333.toInt(), p.surface)
    }

    @Test fun parseColorAcceptsOnlyRgbAndArgb() {
        assertEquals(0xFF00E5FF.toInt(), FleetThemeParser.parseColor("#00E5FF"))
        assertEquals(0xFF00E5FF.toInt(), FleetThemeParser.parseColor("#00e5ff"))
        assertEquals(0x00000000, FleetThemeParser.parseColor("#00000000"))
        assertEquals(0xCC1A1D2B.toInt(), FleetThemeParser.parseColor("#CC1A1D2B"))
        assertNull(FleetThemeParser.parseColor(null))
        assertNull(FleetThemeParser.parseColor(""))
        assertNull(FleetThemeParser.parseColor("   "))
        assertNull(FleetThemeParser.parseColor("00E5FF"))
        assertNull(FleetThemeParser.parseColor("#FFF"))
        assertNull(FleetThemeParser.parseColor("#1234567"))
        assertNull(FleetThemeParser.parseColor("#FF00E5FF00"))
        assertNull(FleetThemeParser.parseColor("#+0E5FF"))
        assertNull(FleetThemeParser.parseColor("#00E5FF "))
    }
}
