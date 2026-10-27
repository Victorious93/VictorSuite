package com.termux.zerocore.hak5

/**
 * Heuristic severity classifier for runtime-discovered Pineapple actions (plan §4).
 *
 * The Tetra firmware varies across versions, so module/action names are NEVER
 * hardcoded or trusted blindly. Everything we discover at runtime is classified
 * conservatively: anything we cannot positively recognise as read-only defaults to
 * [Hak5ActionSeverity.POTENTIALLY_INVASIVE] so it falls behind per-action
 * confirmation ([Hak5ActionConfirm]) with a clear warning. We would rather ask for
 * one confirmation too many than auto-run something invasive.
 */
object Hak5ActionClassifier {

    /** Verbs that are safe reads — surfaced without an invasive warning (still gated). */
    private val READ_ONLY_PREFIXES = listOf(
        "get", "list", "read", "show", "status", "fetch", "view",
        "describe", "is", "has", "query", "count", "current", "info"
    )

    /** Verbs that change device configuration/state but are not themselves attacks. */
    private val CONFIG_PREFIXES = listOf(
        "set", "update", "save", "apply", "write", "edit", "change",
        "add", "remove", "delete", "create", "install", "uninstall",
        "configure", "config", "clear", "reset", "import", "export"
    )

    /**
     * Substrings that mark a potentially invasive / offensive action. Matched anywhere
     * in the action or module name. Deliberately broad — false positives only cost an
     * extra confirmation, false negatives could auto-run an attack.
     */
    private val INVASIVE_KEYWORDS = listOf(
        "attack", "deauth", "inject", "spoof", "evil", "pineap", "handshake",
        "capture", "start", "run", "launch", "exec", "beacon", "flood",
        "karma", "recon", "dwall", "dos", "mitm", "intercept", "sniff",
        "jam", "crack", "harvest", "clone", "rogue", "phish", "poison",
        "scan", "probe", "enable", "activate", "trigger", "fire", "send"
    )

    /**
     * Classify by action name, optionally considering the module name for context.
     * Unknown shapes return [Hak5ActionSeverity.POTENTIALLY_INVASIVE].
     */
    fun classify(action: String?, module: String? = null): Hak5ActionSeverity {
        val a = action?.trim()?.lowercase().orEmpty()
        val m = module?.trim()?.lowercase().orEmpty()
        if (a.isEmpty()) return Hak5ActionSeverity.POTENTIALLY_INVASIVE

        // An invasive verb in the ACTION wins over everything.
        if (INVASIVE_KEYWORDS.any { a.contains(it) }) {
            return Hak5ActionSeverity.POTENTIALLY_INVASIVE
        }
        // A clear read verb stays read-only even inside an "attacky" module — reading
        // status/results never changes device state.
        if (READ_ONLY_PREFIXES.any { a.startsWith(it) }) {
            return Hak5ActionSeverity.READ_ONLY
        }
        if (CONFIG_PREFIXES.any { a.startsWith(it) }) {
            return Hak5ActionSeverity.CONFIG
        }
        // Unknown verb: an invasive-looking module name tips it to invasive; otherwise
        // the safe default is still invasive (require confirmation).
        if (m.isNotEmpty() && INVASIVE_KEYWORDS.any { m.contains(it) }) {
            return Hak5ActionSeverity.POTENTIALLY_INVASIVE
        }
        return Hak5ActionSeverity.POTENTIALLY_INVASIVE
    }

    fun isInvasive(severity: Hak5ActionSeverity): Boolean =
        severity == Hak5ActionSeverity.POTENTIALLY_INVASIVE
}
