package com.termux.victor.assistant

/** What the UI/helper should do with a `??` request. Nothing here executes anything. */
sealed class Resolution {
    /** A concrete command to PREVIEW. [verdict].destructive means the second, labelled confirmation is required. */
    data class Proposed(
        val intent: Intent,
        val command: String,
        val usesRoot: Boolean,
        val verdict: Verdict,
    ) : Resolution()

    /** The best variant needs root but none is available. Show [command] and explain; do not run. */
    data class NeedsRoot(val intent: Intent, val command: String) : Resolution()

    /** No confident match. Never runnable; [suggestions] are titles only. */
    data class NoMatch(val suggestions: List<Intent>) : Resolution()

    /** The line was not a `??` query. */
    object NotAQuery : Resolution()
}

class AssistantResolver(private val table: IntentTable) {
    private val matcher = IntentMatcher(table)

    fun resolve(line: String, rootAvailable: Boolean): Resolution {
        val query = AssistantQuery.parse(line) ?: return Resolution.NotAQuery
        if (query.isEmpty()) return Resolution.NoMatch(emptyList())
        val ranked = matcher.rank(query)
        val hit = IntentMatcher.confident(ranked)
            ?: return Resolution.NoMatch(ranked.take(3).map { it.intent })
        return forIntent(hit.intent, rootAvailable)
    }

    fun forIntent(intent: Intent, rootAvailable: Boolean): Resolution {
        val user = intent.commandUser
        val root = intent.commandRoot
        return when {
            root != null && rootAvailable -> {
                // Root variants run through su, so the resolved command contains `su` and always confirms.
                val cmd = "su -c " + shQuote(root)
                Resolution.Proposed(intent, cmd, true, merge(cmd, root))
            }
            user != null -> Resolution.Proposed(intent, user, false, DestructiveClassifier.classify(user))
            else -> Resolution.NeedsRoot(intent, "su -c " + shQuote(root!!))
        }
    }

    private fun merge(outer: String, inner: String): Verdict {
        val a = DestructiveClassifier.classify(outer)
        val b = DestructiveClassifier.classify(inner)
        return Verdict(a.destructive || b.destructive, (a.reasons + b.reasons).distinct())
    }

    private fun shQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
