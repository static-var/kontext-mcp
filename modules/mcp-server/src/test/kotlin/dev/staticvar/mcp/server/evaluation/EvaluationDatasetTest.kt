package dev.staticvar.mcp.server.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluationDatasetTest {
    @Test
    fun `load bundled evaluation dataset`() {
        val expectations = EvaluationDataset.load()
        assertEquals(24, expectations.size)
        val first = expectations.first()
        assertTrue(first.query.contains("LaunchedEffect"))
        assertTrue(first.expectedKeywords.contains("LaunchedEffect"))
    }
}
