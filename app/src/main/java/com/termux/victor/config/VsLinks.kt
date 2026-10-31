package com.termux.victor.config

/**
 * Every external URL the app opens or fetches lives here, so links can be changed in one place.
 * Edit the values below; nothing else in the app should hard-code a URL.
 *
 * (Package will move to com.termux.victor.config in a later debrand batch.)
 */
object VsLinks {
    // --- VictorSuite project ---
    const val REPO = "https://github.com/Victorious93/VictorSuite"
    const val RELEASES = "$REPO/releases"
    const val ISSUES = "$REPO/issues"

    /** GitHub Pages site built from docs/ (live once Pages is enabled for the repo). */
    const val DOCS_SITE = "https://victorious93.github.io/VictorSuite/"

    /** In-app help / manual. Points at docs/MANUAL.md until the Pages site is live, then switch to DOCS_SITE. */
    const val HELP = "$REPO/blob/main/docs/MANUAL.md"

    /**
     * Plugin / module catalog, served by GitHub Pages from docs/plugins/catalog.json.
     * Replaces the upstream ZeroTermux module download server.
     */
    const val PLUGIN_CATALOG = "${DOCS_SITE}plugins/catalog.json"

    /** Release check: newest release including pre-releases (/releases/latest skips pre-releases and 404s). */
    const val RELEASES_API = "https://api.github.com/repos/Victorious93/VictorSuite/releases?per_page=1"

    // --- Feeds (served from this repo's remote/ directory) ---
    const val FEED_BASE = "https://raw.githubusercontent.com/Victorious93/VictorSuite/main/remote"
    const val FEED_REPOSITORY = "$FEED_BASE/repository/main.json"
    const val FEED_ONLINE_SCRIPTS = "$FEED_BASE/on_line_sh/main.json"
    const val FEED_PLUGINS = "$FEED_BASE/plugins/main.json"
    const val FEED_REPOSITORY_BROWSE = "$REPO/tree/main/remote/repository"

    // --- Sister apps ---
    const val FILE_MANAGER_REPO = "https://github.com/Victorious93/File-Manager"
    const val ANDROID_CODE_STUDIO_REPO = "https://github.com/Victorious93/android-code-studio"
    const val ROOTFORGE_RELEASES = "https://github.com/Victorious93/rootforge-os/releases"
    const val DROIDCOMMAND_RELEASES = "https://github.com/Victorious93/droidcommand-ai/releases"
    /** Placeholder: victorious-andrax is private and has no releases yet; update when one is published. */
    const val ANDRAX_RELEASES = "https://github.com/Victorious93/victorious-andrax/releases"

    // --- AI providers ---
    const val DEEPSEEK_PLATFORM = "https://platform.deepseek.com/"
    const val DEEPSEEK_API = "https://api.deepseek.com/chat/completions"
    const val CLAUDE_API = "https://api.anthropic.com/v1/messages"

    // --- Third-party downloads and credits ---
    const val TAILSCALE_STABLE = "https://pkgs.tailscale.com/stable/"
    const val FILEBROWSER = "https://github.com/filebrowser/filebrowser"
    const val ANDROID_WEB_SERV = "https://github.com/joinAero/AndroidWebServ"
    const val WINDOWS11_DOWNLOAD = "https://www.microsoft.com/software-download/windows11"
}
