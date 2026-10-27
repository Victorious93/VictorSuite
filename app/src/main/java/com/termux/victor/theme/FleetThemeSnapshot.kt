package com.termux.victor.theme

/**
 * Parsed palette. A null entry means the key was absent, blank or not valid `#RRGGBB` /
 * `#AARRGGBB`; the consumer must keep its own default for the mapped role (per spec).
 * Colors are ARGB ints.
 */
data class FleetPalette(
    val background: Int? = null,
    val surface: Int? = null,
    val primary: Int? = null,
    val secondary: Int? = null,
    val onBackground: Int? = null,
    val muted: Int? = null,
    val danger: Int? = null,
    val stale: Int? = null,
    val tile: Int? = null,
) {
    fun isEmpty(): Boolean = listOf(background, surface, primary, secondary, onBackground, muted, danger, stale, tile)
        .all { it == null }

    /**
     * Color for a Material3-style role, following the spec's "Palette keys -> Material3 roles"
     * table. Returns null when the mapped palette key is missing (keep the app default).
     */
    fun colorFor(role: FleetRole): Int? = when (role) {
        FleetRole.BACKGROUND -> background
        FleetRole.ON_PRIMARY, FleetRole.ON_SECONDARY, FleetRole.ON_ERROR -> background
        FleetRole.SURFACE -> surface
        FleetRole.PRIMARY -> primary
        FleetRole.SECONDARY -> secondary
        FleetRole.ON_BACKGROUND, FleetRole.ON_SURFACE -> onBackground
        FleetRole.ON_SURFACE_VARIANT, FleetRole.OUTLINE -> muted
        FleetRole.ERROR -> danger
        FleetRole.SURFACE_VARIANT -> tile
        FleetRole.STALE_ACCENT -> stale
    }
}

/** Roles the palette can drive. `STALE_ACCENT` is the fleet-only `stale` key (not an M3 role). */
enum class FleetRole {
    BACKGROUND, ON_PRIMARY, ON_SECONDARY, ON_ERROR,
    SURFACE,
    PRIMARY,
    SECONDARY,
    ON_BACKGROUND, ON_SURFACE,
    ON_SURFACE_VARIANT, OUTLINE,
    ERROR,
    SURFACE_VARIANT,
    STALE_ACCENT,
}

/** A validated schemaVersion 1 snapshot. */
data class FleetThemeSnapshot(
    val schemaVersion: Int,
    val mode: String,
    val iconStyle: String,
    val iconPackPackage: String,
    val updatedAtMs: Long,
    val palette: FleetPalette,
)
