package com.termux.victor.motd

import com.termux.victor.config.VsLinks
import java.io.File

/**
 * VictorSuite login banner written to $PREFIX/etc/motd (the file Termux's `login` script cats).
 *
 * The bootstrap zip ships the stock Termux motd; we never modify the zip. Instead, after bootstrap
 * and on every app start, [installIfStock] replaces etc/motd only when it is still the stock text
 * (ignoring line endings / trailing spaces), so a user-edited motd is never clobbered.
 */
object VsMotd {
    const val DOCS = VsLinks.DOCS_SITE
    const val REPO = VsLinks.REPO
    const val ISSUES = VsLinks.ISSUES

    /** "VICTORIOUS" in figlet "small"; at most 49 columns so it fits a phone in portrait. */
    @JvmField
    val BANNER: List<String> = listOf(
        "__   _____ ___ _____ ___  ___ ___ ___  _   _ ___",
        "\\ \\ / /_ _/ __|_   _/ _ \\| _ \\_ _/ _ \\| | | / __|",
        " \\ V / | | (__  | || (_) |   /| | (_) | |_| \\__ \\",
        "  \\_/ |___\\___| |_| \\___/|_|_\\___\\___/ \\___/|___/",
    )

    /** Stock motd from termux-tools as shipped in the bootstrap (termux.dev links). */
    const val STOCK_MOTD = "Welcome to Termux!\n\nDocs:       https://termux.dev/docs\nDonate:     https://termux.dev/donate\nCommunity:  https://termux.dev/community\n\nWorking with packages:\n\n - Search:  pkg search <query>\n - Install: pkg install <package>\n - Upgrade: pkg upgrade\n\nSubscribing to additional repositories:\n\n - Root:    pkg install root-repo\n - X11:     pkg install x11-repo\n\nFor fixing any repository issues,\ntry 'termux-change-repo' command.\n\nReport issues at https://termux.dev/issues\n"

    @JvmStatic
    fun text(): String = buildString {
        append('\n')
        BANNER.forEach { append(it).append('\n') }
        append("\n")
        append("Welcome to VictorSuite!\n\n")
        // Label and URL on separate lines so nothing exceeds ~50 columns on a phone.
        append("Docs:\n").append(DOCS).append('\n')
        append("Source:\n").append(REPO).append('\n')
        append("Issues:\n").append(ISSUES).append('\n')
        append("\n")
        append("Getting started:\n")
        append(" - Update:  pkg update\n")
        append(" - Upgrade: pkg upgrade\n")
        append(" - Search:  pkg search <query>\n")
        append(" - Install: pkg install <package>\n")
        append("\n")
        append("Extra repos:\n")
        append(" - pkg install root-repo  (root packages)\n")
        append(" - pkg install x11-repo   (X11/GUI packages)\n")
        append(" - pkg install tur-repo   (Termux User Repo)\n")
        append("\n")
        append("Repo issues? Run: termux-change-repo\n")
    }

    private fun normalize(s: String): String =
        s.replace("\r\n", "\n").lines().joinToString("\n") { it.trimEnd() }.trim()

    /** True if [content] is the stock Termux motd (ignoring line endings / trailing whitespace). */
    @JvmStatic
    fun isStock(content: String?): Boolean = content != null && normalize(content) == normalize(STOCK_MOTD)

    /** Decide whether to write our motd over [existing] (null = file absent). */
    @JvmStatic
    fun shouldReplace(existing: String?): Boolean = existing != null && isStock(existing)

    /**
     * Replace prefix/etc/motd with [text] if it is still stock. Returns true if written.
     * Never throws; a missing file is left alone (the user may have deleted it on purpose).
     */
    @JvmStatic
    fun installIfStock(prefixDir: File): Boolean {
        return try {
            val motd = File(prefixDir, "etc/motd")
            if (!motd.isFile) return false
            if (!shouldReplace(motd.readText())) return false
            val tmp = File(motd.parentFile, "motd.vs-tmp")
            tmp.writeText(text())
            tmp.renameTo(motd) || run { motd.writeText(text()); tmp.delete(); true }
        } catch (t: Throwable) {
            false
        }
    }
}
