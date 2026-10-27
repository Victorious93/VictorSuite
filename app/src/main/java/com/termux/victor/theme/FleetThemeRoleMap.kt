package com.termux.victor.theme

/**
 * Maps VictorSuite's built-in drawer/settings colors (res/values/colors.xml) to fleet roles.
 *
 * The drawers (layout_menu_all.xml, include_right_menu.xml) and the VictorSuite settings screens
 * are hand-coloured XML, not Material3 themes. Rather than rewrite those layouts, the applier
 * looks at each view's *original* colour and, if it is one of the built-in chrome colours below,
 * swaps it for the matching palette role. Anything else (status red, icons, terminal) is left alone.
 *
 * Pure JVM so the mapping is unit-tested.
 */
object FleetThemeRoleMap {
    /** @color/color_2b2b2b: drawer and settings page background. */
    const val BUILTIN_BACKGROUND = 0xFF2B2B2B.toInt()
    /** @color/color_55000000: translucent cards/tiles inside the drawers and settings. */
    const val BUILTIN_TILE = 0x55000000
    /** @color/color_ffffff and @color/white: primary text. */
    const val BUILTIN_TEXT = 0xFFFFFFFF.toInt()
    /** @color/color_48baf3: link-style accent text. */
    const val BUILTIN_ACCENT_TEXT = 0xFF48BAF3.toInt()
    /** @color/md_grey_500: secondary/explanatory text. */
    const val BUILTIN_MUTED_TEXT = 0xFF9E9E9E.toInt()

    /** Role for a container background colour, or null to leave it untouched. */
    fun backgroundRole(originalColor: Int): FleetRole? = when (originalColor) {
        BUILTIN_BACKGROUND -> FleetRole.BACKGROUND
        BUILTIN_TILE -> FleetRole.SURFACE_VARIANT
        else -> null
    }

    /** Role for a text colour, or null to leave it untouched. */
    fun textRole(originalColor: Int): FleetRole? = when (originalColor) {
        BUILTIN_TEXT -> FleetRole.ON_BACKGROUND
        BUILTIN_ACCENT_TEXT -> FleetRole.PRIMARY
        BUILTIN_MUTED_TEXT -> FleetRole.ON_SURFACE_VARIANT
        else -> null
    }

    /**
     * Colour to show: the palette colour for [role] when the snapshot has it, otherwise the
     * view's own [originalColor] (spec: missing key keeps the app default, never substitute).
     */
    fun resolve(palette: FleetPalette?, role: FleetRole?, originalColor: Int): Int {
        if (palette == null || role == null) return originalColor
        return palette.colorFor(role) ?: originalColor
    }
}
