package com.termux.victor.config

import com.termux.shared.termux.VictorSuiteLinks

/**
 * Every external URL the app opens or fetches lives here, so links can be changed in one place.
 * Edit the values below (or the overlapping ones in VictorSuiteLinks); nothing else in the app
 * should hard-code a URL.
 */
object VsLinks {
    // --- VictorSuite project ---
    // These four are defined in termux-shared so About / crash-report text can use them too.
    const val REPO = VictorSuiteLinks.REPO
    const val RELEASES = VictorSuiteLinks.RELEASES
    const val ISSUES = VictorSuiteLinks.ISSUES

    /** GitHub Pages site built from docs/. */
    const val DOCS_SITE = VictorSuiteLinks.DOCS_SITE

    /** In-app help / manual on the Pages site (MANUAL.html, verified live). */
    const val HELP = VictorSuiteLinks.HELP

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
    /** Placeholder: victordrax is private and has no releases yet; update when one is published. */
    const val VICTORDRAX_RELEASES = "https://github.com/Victorious93/victordrax/releases"

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
