package dev.staticvar.mcp.server.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals

class RetrievalMetricsTest {

    @Test
    fun `precision at K counts relevant entries`() {
        val flags = listOf(true, false, true, false)
        val precision = RetrievalMetrics.precisionAtK(flags, 3)
        assertEquals(2.0 / 3.0, precision, 1e-6)
    }

    @Test
    fun `recall at K respects total relevant`() {
        val flags = listOf(false, true, false, true)
        val recall = RetrievalMetrics.recallAtK(flags, totalRelevant = 3, k = 4)
        assertEquals(2.0 / 3.0, recall, 1e-6)
    }

    @Test
    fun `reciprocal rank returns first relevant inverse index`() {
        val flags = listOf(false, false, true, true)
        val rr = RetrievalMetrics.reciprocalRank(flags)
        assertEquals(1.0 / 3.0, rr, 1e-6)
    }

    @Test
    fun `mean reciprocal rank averages queries`() {
        val rr = RetrievalMetrics.meanReciprocalRank(
            listOf(
                listOf(true, false),
                listOf(false, true),
                listOf(false, false)
            )
        )
        val expected = (1.0 + 0.5 + 0.0) / 3.0
        assertEquals(expected, rr, 1e-6)
    }
}
