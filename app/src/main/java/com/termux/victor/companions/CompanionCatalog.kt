package com.termux.victor.companions

/**
 * The VictorSuite companion add-ons (Victorious forks of the Termux plugin apps).
 * Every one is optional: VictorSuite works fully without any of them, and each
 * companion keeps working on its own. VictorTasker is not here on purpose; it
 * lives inside DroidCommand AI instead of being a Termux:Tasker fork.
 *
 * [packageIds] lists the ids we recognise, newest first. The Termux plugin
 * model needs the add-on to share VictorSuite's `sharedUserId` (`com.termux`)
 * and signing key, so the forks are expected to keep the upstream ids until
 * the owner decides otherwise.
 */
data class CompanionSpec(
    val id: String,
    val displayName: String,
    val forkRepo: String,
    val upstreamRepo: String,
    val packageIds: List<String>,
    val summary: String,
    val needsSharedUser: Boolean = true,
)

object CompanionCatalog {
    const val OWNER = "Victorious93"

    val ALL: List<CompanionSpec> = listOf(
        CompanionSpec(
            "x11", "VictorX11", "$OWNER/VictorX11", "termux/termux-x11",
            listOf("com.termux.x11"),
            "X11 server for desktop Linux apps from proot distros.",
            needsSharedUser = false,
        ),
        CompanionSpec(
            "gui", "VictorGUI", "$OWNER/VictorGUI", "termux/termux-gui",
            listOf("com.termux.gui"),
            "Native Android views driven from scripts.",
        ),
        CompanionSpec(
            "float", "VictorFloat", "$OWNER/VictorFloat", "termux/termux-float",
            listOf("com.termux.window"),
            "Floating terminal window over other apps.",
        ),
        CompanionSpec(
            "widget", "VictorWidget", "$OWNER/VictorWidget", "termux/termux-widget",
            listOf("com.termux.widget"),
            "Home-screen shortcuts to scripts in ~/.shortcuts.",
        ),
        CompanionSpec(
            "boot", "VictorBoot", "$OWNER/VictorBoot", "termux/termux-boot",
            listOf("com.termux.boot"),
            "Runs scripts in ~/.termux/boot at device start.",
        ),
        CompanionSpec(
            "styling", "VictorStyling", "$OWNER/VictorStyling", "termux/termux-styling",
            listOf("com.termux.styling"),
            "Color schemes and fonts, including the Victorious neon theme.",
        ),
        CompanionSpec(
            "api", "VictorAPI", "$OWNER/VictorAPI", "termux/termux-api",
            listOf("com.termux.api"),
            "Android APIs (battery, clipboard, sensors...) for the shell.",
        ),
    )

    fun byId(id: String): CompanionSpec? = ALL.firstOrNull { it.id == id }
}
