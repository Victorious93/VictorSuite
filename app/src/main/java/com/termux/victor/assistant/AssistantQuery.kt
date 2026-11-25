package com.termux.victor.assistant

/** Parses the `??` prefix. Pure and offline; it never logs the query. */
object AssistantQuery {
    // "??" or "?? " at the start of the line (leading spaces allowed). "???" is not a query.
    private val PREFIX = Regex("""^\s*\?\?(?!\?)\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)

    /** Returns the plain-English request, "" for a bare `??`, or null when the line is not an assistant query. */
    fun parse(line: String): String? {
        val m = PREFIX.matchEntire(line) ?: return null
        return m.groupValues[1].trim()
    }
}
