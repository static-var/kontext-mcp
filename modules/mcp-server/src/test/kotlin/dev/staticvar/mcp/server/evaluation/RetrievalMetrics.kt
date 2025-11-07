package dev.staticvar.mcp.server.evaluation

object RetrievalMetrics {
    fun precisionAtK(relevanceFlags: List<Boolean>, k: Int): Double {
        require(k > 0) { "k must be positive" }
        if (relevanceFlags.isEmpty()) return 0.0
        val limit = k.coerceAtMost(relevanceFlags.size)
        val relevant = relevanceFlags.take(limit).count { it }
        return relevant.toDouble() / limit
    }

    fun recallAtK(relevanceFlags: List<Boolean>, totalRelevant: Int, k: Int): Double {
        require(totalRelevant >= 0) { "totalRelevant must be non-negative" }
        if (totalRelevant == 0) return 0.0
        if (relevanceFlags.isEmpty()) return 0.0
        val limit = k.coerceAtMost(relevanceFlags.size)
        val relevantFound = relevanceFlags.take(limit).count { it }
        return relevantFound.toDouble() / totalRelevant
    }

    fun reciprocalRank(relevanceFlags: List<Boolean>): Double {
        relevanceFlags.forEachIndexed { index, isRelevant ->
            if (isRelevant) {
                return 1.0 / (index + 1)
            }
        }
        return 0.0
    }

    fun meanReciprocalRank(allFlags: List<List<Boolean>>): Double {
        if (allFlags.isEmpty()) return 0.0
        val sum = allFlags.sumOf { reciprocalRank(it) }
        return sum / allFlags.size
    }
}
