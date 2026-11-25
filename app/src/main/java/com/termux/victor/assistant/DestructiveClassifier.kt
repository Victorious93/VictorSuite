package com.termux.victor.assistant

data class Verdict(val destructive: Boolean, val reasons: List<String>) {
    companion object { val SAFE = Verdict(false, emptyList()) }
}

/**
 * Denylist + path/operator heuristics run on the RESOLVED command (never on the English query).
 * When unsure it confirms. Overlays and the intent table's own hints cannot weaken it.
 */
object DestructiveClassifier {

    private val ALWAYS = mapOf(
        "rm" to "This would delete files (rm)",
        "rmdir" to "This would remove directories (rmdir)",
        "shred" to "This would overwrite and delete files (shred)",
        "truncate" to "This would truncate files (truncate)",
        "unlink" to "This would delete a file (unlink)",
        "dd" to "This would run dd (raw disk writes)",
        "mkfs" to "This would create a filesystem (mkfs)",
        "fdisk" to "This would edit partitions (fdisk)",
        "parted" to "This would edit partitions (parted)",
        "sgdisk" to "This would edit partitions (sgdisk)",
        "wipefs" to "This would wipe filesystem signatures (wipefs)",
        "format" to "This would format storage (format)",
        "reboot" to "This would reboot the device",
        "shutdown" to "This would shut the device down",
        "halt" to "This would halt the device",
        "poweroff" to "This would power the device off",
        "fastboot" to "This would talk to the bootloader (fastboot)",
        "flash" to "This would flash firmware",
        "lpmake" to "This would build super partition images (lpmake)",
        "avbctl" to "This would change verified boot (avbctl)",
        "su" to "This would run with root privileges (su)",
        "sudo" to "This would run with elevated privileges (sudo)",
        "ksu" to "This would run with root privileges (ksu)",
        "daemonsu" to "This would run with root privileges (daemonsu)",
        "kill" to "This would kill processes (kill)",
        "killall" to "This would kill processes (killall)",
        "pkill" to "This would kill processes (pkill)",
        "setenforce" to "This would change the SELinux mode (setenforce)",
        "eval" to "This runs dynamically built code (eval)",
    )

    /** Commands that write to their path arguments. */
    private val WRITERS = setOf(
        "cp", "mv", "install", "tee", "ln", "chmod", "chown", "chcon", "touch", "mkdir",
        "mount", "umount", "sed", "rsync", "patch",
    )

    private val PROTECTED = listOf("/system", "/vendor", "/product", "/system_ext", "/boot", "/dev/block", "/odm")

    private val WRAPPERS = setOf("command", "exec", "nohup", "time", "env", "nice", "busybox", "toybox", "xargs", "stdbuf", "timeout")

    fun classify(command: String): Verdict {
        val reasons = LinkedHashSet<String>()
        if (command.isBlank()) return Verdict(true, listOf("The command is empty or could not be read"))
        if (hasUnbalancedQuotes(command)) reasons += "The command could not be parsed safely"

        // Redirects into protected paths, e.g. `echo x > /system/f`.
        Regex(""">>?\s*(\S+)""").findAll(command).forEach {
            if (isProtected(it.groupValues[1].trim('"', '\''))) reasons += "This would write to ${it.groupValues[1]}"
        }
        // Command substitution and backticks hide inner commands: classify them too.
        val flat = command.replace("$(", " ; ").replace(")", " ; ").replace("`", " ; ")

        for (seg in splitSegments(flat)) classifySegment(seg, reasons)
        return Verdict(reasons.isNotEmpty(), reasons.toList())
    }

    private fun classifySegment(seg: String, reasons: MutableSet<String>) {
        var w = shellWords(seg).dropWhile { it.contains('=') && !it.startsWith("-") && !it.startsWith("/") }
        while (w.isNotEmpty() && baseName(w[0]) in WRAPPERS) {
            w = w.drop(1).dropWhile { it.startsWith("-") }
        }
        if (w.isEmpty()) return
        val cmd = baseName(w[0])
        val args = w.drop(1)

        ALWAYS[cmd]?.let { reasons += it }
        if (cmd.startsWith("mkfs")) reasons += ALWAYS.getValue("mkfs")
        if (cmd in setOf("sh", "bash", "ash", "zsh", "dash") && args.any { it == "-c" || (it.startsWith("-") && it.endsWith("c")) }) {
            reasons += "This runs a nested shell command"
            args.lastOrNull()?.let { inner -> if (inner != "-c") for (s in splitSegments(inner)) classifySegment(s, reasons) }
        }
        if (cmd == "pm" && args.firstOrNull() in setOf("uninstall", "clear")) reasons += "This would remove an app or its data (pm ${args[0]})"
        if (cmd == "cmd" && args.size >= 2 && args[0] == "package" && args[1] in setOf("uninstall", "clear")) reasons += "This would remove an app or its data"
        if (cmd == "iptables" || cmd == "ip6tables") {
            if (args.any { it == "-F" || it == "--flush" || it == "-X" }) reasons += "This would flush firewall rules"
        }
        if (cmd == "recovery" || (cmd == "reboot")) reasons += "This would reboot into recovery or the bootloader"
        if (cmd == "mount" && args.any { it.contains("remount") }) reasons += "This would remount a filesystem"
        if (cmd == "magisk" && args.firstOrNull() in setOf("--remove-modules", "--uninstall", "--restore")) reasons += "This would change Magisk state"
        if ((cmd == "chmod" || cmd == "chown") && args.contains("-R") && args.any { it == "/" || it == "/data" }) reasons += "This would change permissions recursively on a system path"

        if (cmd in WRITERS || (cmd == "sed" && args.any { it.startsWith("-i") })) {
            val writes = cmd != "sed" || args.any { it.startsWith("-i") }
            if (writes) args.filter { !it.startsWith("-") }.forEach {
                if (isProtected(it)) reasons += "This would write to $it"
            }
        }
        // Any `rm`-like or kill-like aimed at the system is already flagged above; extra system-process detail:
        if (args.any { it == "system_server" || it == "zygote" }) reasons += "This targets a core Android process"
    }

    private fun isProtected(path: String): Boolean =
        PROTECTED.any { path == it || path.startsWith("$it/") }

    private fun baseName(s: String): String = s.substringAfterLast('/')

    private fun splitSegments(s: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var q: Char? = null
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (q != null) {
                sb.append(c); if (c == q) q = null
            } else if (c == '\'' || c == '"') {
                q = c; sb.append(c)
            } else if (c == ';' || c == '|' || c == '&' || c == '\n') {
                out += sb.toString(); sb.clear()
            } else sb.append(c)
            i++
        }
        out += sb.toString()
        return out.filter { it.isNotBlank() }
    }

    /** Minimal shell word split (quotes removed, no expansion). */
    private fun shellWords(s: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var q: Char? = null
        var any = false
        for (c in s) {
            if (q != null) { if (c == q) q = null else sb.append(c) }
            else if (c == '\'' || c == '"') { q = c; any = true }
            else if (c.isWhitespace()) { if (sb.isNotEmpty() || any) { out += sb.toString(); sb.clear(); any = false } }
            else sb.append(c)
        }
        if (sb.isNotEmpty() || any) out += sb.toString()
        return out
    }

    private fun hasUnbalancedQuotes(s: String): Boolean {
        var q: Char? = null
        for (c in s) {
            if (q != null) { if (c == q) q = null } else if (c == '\'' || c == '"') q = c
        }
        return q != null
    }
}
