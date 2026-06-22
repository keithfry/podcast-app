package com.frybynite.podlore.ui.player

import com.frybynite.podlore.domain.model.TranscriptSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranscriptUtilsTest {
    private fun seg(start: Float, end: Float, text: String = "text") =
        TranscriptSegment(start, end, text)

    @Test fun `returns segments within chapter range`() {
        val segments = listOf(seg(0f, 3f), seg(3f, 7f), seg(17f, 25f))
        val result = segmentsForChapter(segments, 0f, 17f)
        assertEquals(2, result.size)
        assertEquals(0f, result[0].startTimeSec)
        assertEquals(3f, result[1].startTimeSec)
    }

    @Test fun `excludes segment at next chapter start boundary`() {
        val segments = listOf(seg(10f, 15f), seg(17f, 25f))
        val result = segmentsForChapter(segments, 0f, 17f)
        assertEquals(1, result.size)
        assertEquals(10f, result[0].startTimeSec)
    }

    @Test fun `last chapter captures all remaining segments via MAX_VALUE`() {
        val segments = listOf(seg(17f, 25f), seg(25f, 40f))
        val result = segmentsForChapter(segments, 17f, Float.MAX_VALUE)
        assertEquals(2, result.size)
    }

    @Test fun `returns empty for chapter with no segments in range`() {
        val segments = listOf(seg(0f, 3f), seg(3f, 7f))
        val result = segmentsForChapter(segments, 50f, 100f)
        assertTrue(result.isEmpty())
    }

    @Test fun `empty segment list returns empty`() {
        val result = segmentsForChapter(emptyList(), 0f, 100f)
        assertTrue(result.isEmpty())
    }

    @Test fun `segment exactly at chapter start is included`() {
        val segments = listOf(seg(17f, 25f))
        val result = segmentsForChapter(segments, 17f, 30f)
        assertEquals(1, result.size)
    }
}
