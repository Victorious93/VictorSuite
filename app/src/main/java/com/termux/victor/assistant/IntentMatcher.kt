package com.termux.victor.assistant

data class IntentMatch(val intent: Intent, val score: Double)

/** Offline keyword + fuzzy matcher. No network, no logging. */
class IntentMatcher(private val table: IntentTable) {

    /** Matches sorted best-first, only those at or above [SUGGEST_THRESHOLD]. */
    fun rank(query: String): List<IntentMatch> {
        val q = tokens(query)
        if (q.isEmpty()) return emptyList()
        return table.intents
            .map { IntentMatch(it, scoreIntent(it, q)) }
            .filter { it.score >= SUGGEST_THRESHOLD }
            .sortedByDescending { it.score }
    }

    private fun scoreIntent(intent: Intent, q: List<String>): Double =
        (intent.keywords + intent.aliases).maxOfOrNull { phraseScore(tokens(it), q) } ?: 0.0

    private fun phraseScore(p: List<String>, q: List<String>): Double {
        if (p.isEmpty()) return 0.0
        var sum = 0.0
        val usedQ = HashSet<Int>()
        for (pt in p) {
            var best = 0.0
            var bestIdx = -1
            q.forEachIndexed { i, qt ->
                val s = tokenSim(pt, qt)
                if (s > best) { best = s; bestIdx = i }
            }
            if (bestIdx >= 0) usedQ += bestIdx
            sum += best
        }
        val cover = sum / p.size
        val qcover = usedQ.size.toDouble() / q.size
        return cover * (0.7 + 0.3 * qcover)
    }

    private fun tokenSim(a: String, b: String): Double = when {
        a == b -> 1.0
        a.length >= 4 && b.length >= 4 && editDistance(a, b) <= 1 -> 0.85
        else -> 0.0
    }

    companion object {
        const val MATCH_THRESHOLD = 0.75
        const val SUGGEST_THRESHOLD = 0.45
        private const val AMBIGUITY_GAP = 0.05

        fun tokens(s: String): List<String> =
            s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

        /** The single confident winner, or null when nothing is confident or the top two tie. */
        fun confident(ranked: List<IntentMatch>): IntentMatch? {
            val top = ranked.firstOrNull() ?: return null
            if (top.score < MATCH_THRESHOLD) return null
            val second = ranked.getOrNull(1)
            if (second != null && second.score >= MATCH_THRESHOLD && top.score - second.score < AMBIGUITY_GAP) return null
            return top
        }

        fun editDistance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    val c = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + c)
                }
                prev = cur
            }
            return prev[b.length]
        }
    }
}
