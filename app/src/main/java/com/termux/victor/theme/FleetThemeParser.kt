package com.termux.victor.theme

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal

/** Outcome of parsing one provider row. Anything other than [Ok] means "use the built-in theme". */
sealed class FleetThemeParseResult {
    data class Ok(val snapshot: FleetThemeSnapshot) : FleetThemeParseResult()
    data class Rejected(val reason: String) : FleetThemeParseResult()
}

/**
 * Pure parser/validator for the fleet theme row (schemaVersion 1).
 *
 * Input is the single provider row as a column-name -> value map (values as a Cursor yields them:
 * Long/Int, String, Double, null). Rules (FLEET_THEME.md):
 * - `schemaVersion != 1` (column or JSON) is rejected. A row with no schemaVersion at all is rejected.
 * - A malformed `json` column is rejected (any parse error -> built-in theme).
 * - Unknown columns / JSON keys are ignored.
 * - Palette is applied per key: absent, blank or invalid hex leaves that key null (app default),
 *   valid keys still apply. Values come from `json.palette`, else the same-named column.
 */
object FleetThemeParser {

    private val gson = Gson()

    fun parse(row: Map<String, Any?>): FleetThemeParseResult {
        if (row.isEmpty()) return FleetThemeParseResult.Rejected("empty row")

        // 1) Column schemaVersion, if present, must be exactly 1.
        val hasColumnVersion = row.containsKey(FleetThemeContract.COL_SCHEMA_VERSION) &&
            row[FleetThemeContract.COL_SCHEMA_VERSION] != null
        if (hasColumnVersion) {
            val v = columnInt(row[FleetThemeContract.COL_SCHEMA_VERSION])
                ?: return FleetThemeParseResult.Rejected("schemaVersion column is not an integer")
            if (v != FleetThemeContract.SUPPORTED_SCHEMA_VERSION.toLong())
                return FleetThemeParseResult.Rejected("unsupported schemaVersion $v")
        }

        // 2) JSON column, if present, must parse and carry schemaVersion 1.
        val jsonText = row[FleetThemeContract.COL_JSON]
        var json: JsonObject? = null
        if (jsonText != null) {
            if (jsonText !is String) return FleetThemeParseResult.Rejected("json column is not a string")
            if (jsonText.isNotBlank()) {
                json = parseStrictObject(jsonText) ?: return FleetThemeParseResult.Rejected("json column is not a valid JSON object")
                val jv = json.get(FleetThemeContract.COL_SCHEMA_VERSION)
                    ?: return FleetThemeParseResult.Rejected("json has no schemaVersion")
                val jvInt = jsonExactLong(jv) ?: return FleetThemeParseResult.Rejected("json schemaVersion is not an integer")
                if (jvInt != FleetThemeContract.SUPPORTED_SCHEMA_VERSION.toLong())
                    return FleetThemeParseResult.Rejected("unsupported json schemaVersion $jvInt")
            }
        }

        if (!hasColumnVersion && json == null) return FleetThemeParseResult.Rejected("no schemaVersion")

        // 3) Metadata. Wrong types are parse errors; absent values get neutral defaults.
        val mode = stringField(json, row, FleetThemeContract.COL_MODE)
            ?: return FleetThemeParseResult.Rejected("mode has the wrong type")
        val iconStyle = stringField(json, row, FleetThemeContract.COL_ICON_STYLE)
            ?: return FleetThemeParseResult.Rejected("iconStyle has the wrong type")
        val iconPack = stringField(json, row, FleetThemeContract.COL_ICON_PACK_PACKAGE)
            ?: return FleetThemeParseResult.Rejected("iconPackPackage has the wrong type")
        val updatedAt = longField(json, row, FleetThemeContract.COL_UPDATED_AT_MS)
            ?: return FleetThemeParseResult.Rejected("updatedAtMs has the wrong type")

        // 4) Palette, per key.
        val jsonPaletteEl = json?.get(FleetThemeContract.JSON_PALETTE)
        val jsonPalette: JsonObject? = when {
            jsonPaletteEl == null || jsonPaletteEl.isJsonNull -> null
            jsonPaletteEl.isJsonObject -> jsonPaletteEl.asJsonObject
            else -> return FleetThemeParseResult.Rejected("palette is not an object")
        }
        fun color(key: String): Int? {
            val fromJson = jsonPalette?.get(key)?.let { el ->
                if (el.isJsonPrimitive && el.asJsonPrimitive.isString) parseColor(el.asString) else null
            }
            if (fromJson != null) return fromJson
            return (row[key] as? String)?.let { parseColor(it) }
        }
        val palette = FleetPalette(
            background = color(FleetThemeContract.KEY_BACKGROUND),
            surface = color(FleetThemeContract.KEY_SURFACE),
            primary = color(FleetThemeContract.KEY_PRIMARY),
            secondary = color(FleetThemeContract.KEY_SECONDARY),
            onBackground = color(FleetThemeContract.KEY_ON_BACKGROUND),
            muted = color(FleetThemeContract.KEY_MUTED),
            danger = color(FleetThemeContract.KEY_DANGER),
            stale = color(FleetThemeContract.KEY_STALE),
            tile = color(FleetThemeContract.KEY_TILE),
        )

        return FleetThemeParseResult.Ok(
            FleetThemeSnapshot(
                schemaVersion = FleetThemeContract.SUPPORTED_SCHEMA_VERSION,
                mode = mode,
                iconStyle = iconStyle,
                iconPackPackage = iconPack,
                updatedAtMs = updatedAt,
                palette = palette,
            )
        )
    }

    /**
     * `#RRGGBB` (opaque) or `#AARRGGBB` -> ARGB int; anything else (blank, no '#', other lengths,
     * non-hex, surrounding whitespace) -> null.
     */
    fun parseColor(value: String?): Int? {
        if (value == null || value.length != 7 && value.length != 9 || value[0] != '#') return null
        val hex = value.substring(1)
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val parsed = java.lang.Long.parseLong(hex, 16)
        return if (hex.length == 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
    }

    /** Strict (non-lenient) parse of a single JSON object with nothing after it. */
    private fun parseStrictObject(text: String): JsonObject? = try {
        val reader = JsonReader(StringReader(text))
        reader.isLenient = false
        val el: JsonElement = gson.getAdapter(JsonElement::class.java).read(reader)
        if (reader.peek() != JsonToken.END_DOCUMENT) null
        else if (el.isJsonObject) el.asJsonObject else null
    } catch (e: Exception) {
        null
    }

    private fun jsonExactLong(el: JsonElement): Long? {
        if (!el.isJsonPrimitive) return null
        val p: JsonPrimitive = el.asJsonPrimitive
        if (!p.isNumber) return null
        return try {
            BigDecimal(p.asString).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    private fun columnInt(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Short -> value.toLong()
        is String -> if (value.isNotEmpty() && value.length <= 18 && value.all { it in '0'..'9' }) value.toLong() else null
        else -> null
    }

    /** "" if absent everywhere; null if present with the wrong type (caller rejects). */
    private fun stringField(json: JsonObject?, row: Map<String, Any?>, key: String): String? {
        val el = json?.get(key)
        if (el != null && !el.isJsonNull) {
            return if (el.isJsonPrimitive && el.asJsonPrimitive.isString) el.asString else null
        }
        val col = row[key] ?: return ""
        return col as? String
    }

    /** 0 if absent everywhere; null if present but not an integer (caller rejects). */
    private fun longField(json: JsonObject?, row: Map<String, Any?>, key: String): Long? {
        val el = json?.get(key)
        if (el != null && !el.isJsonNull) return jsonExactLong(el)
        val col = row[key] ?: return 0L
        return columnInt(col)
    }
}
