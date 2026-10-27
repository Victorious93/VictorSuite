package com.termux.victor.theme

/**
 * Constants from the VictorLaunch fleet theme contract (schemaVersion 1):
 * https://github.com/Victorious93/victorlaunch/blob/main/docs/specs/FLEET_THEME.md
 *
 * VictorSuite is a read-only client. It never writes the provider.
 */
object FleetThemeContract {
    /** Package that must own the provider (the publisher, VictorLaunch). */
    const val PUBLISHER_PACKAGE = "com.victorious.launch"

    const val AUTHORITY = "com.victorious.launch.theme"
    const val PATH_CURRENT = "current"
    const val URI_CURRENT = "content://$AUTHORITY/$PATH_CURRENT"
    const val MIME_CURRENT = "vnd.android.cursor.item/vnd.com.victorious.launch.theme.current"

    /** Declared by VictorLaunch (signature level). Not relied on; the fleet does not share a key. */
    const val PERMISSION_READ_FLEET_THEME = "com.victorious.launch.permission.READ_FLEET_THEME"

    const val ACTION_FLEET_THEME_CHANGED = "com.victorious.launch.action.FLEET_THEME_CHANGED"

    /** The only schema this client understands. */
    const val SUPPORTED_SCHEMA_VERSION = 1

    // Columns (and the matching top-level JSON keys / broadcast extras).
    const val COL_SCHEMA_VERSION = "schemaVersion"
    const val COL_MODE = "mode"
    const val COL_ICON_STYLE = "iconStyle"
    const val COL_ICON_PACK_PACKAGE = "iconPackPackage"
    const val COL_JSON = "json"
    const val COL_UPDATED_AT_MS = "updatedAtMs"

    /** JSON object holding the palette keys. */
    const val JSON_PALETTE = "palette"

    // Palette keys: both column names and keys under "palette" in the JSON.
    const val KEY_BACKGROUND = "background"
    const val KEY_SURFACE = "surface"
    const val KEY_PRIMARY = "primary"
    const val KEY_SECONDARY = "secondary"
    const val KEY_ON_BACKGROUND = "onBackground"
    const val KEY_MUTED = "muted"
    const val KEY_DANGER = "danger"
    const val KEY_STALE = "stale"
    const val KEY_TILE = "tile"

    val PALETTE_KEYS: List<String> = listOf(
        KEY_BACKGROUND, KEY_SURFACE, KEY_PRIMARY, KEY_SECONDARY, KEY_ON_BACKGROUND,
        KEY_MUTED, KEY_DANGER, KEY_STALE, KEY_TILE,
    )

    val MODES: Set<String> = setOf("LIGHT", "DARK", "NEON_FLEET", "MATERIAL_YOU")
    val ICON_STYLES: Set<String> = setOf("SYSTEM", "MONOCHROME", "TINTED", "ICON_PACK")
}
