package com.frybynite.podcastapp.service

import com.frybynite.podcastapp.domain.model.Chapter
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChapterNavigatorTest {
    private val chapters = listOf(
        Chapter(episodeAudioUrl = "u", startTimeMs = 0,     endTimeMs = 20_000,  title = "Intro", url = null),
        Chapter(episodeAudioUrl = "u", startTimeMs = 20_000, endTimeMs = 60_000,  title = "Ch 2",  url = "https://a.com"),
        Chapter(episodeAudioUrl = "u", startTimeMs = 60_000, endTimeMs = 120_000, title = "Ch 3",  url = null)
    )

    @Test fun `current chapter at 0ms is Intro`() {
        assertEquals("Intro", ChapterNavigator.currentChapter(chapters, 0L)?.title)
    }

    @Test fun `current chapter at 30s is Ch 2`() {
        assertEquals("Ch 2", ChapterNavigator.currentChapter(chapters, 30_000L)?.title)
    }

    @Test fun `current chapter at exactly 60s is Ch 3`() {
        assertEquals("Ch 3", ChapterNavigator.currentChapter(chapters, 60_000L)?.title)
    }

    @Test fun `next chapter from Intro returns Ch 2 start`() {
        assertEquals(20_000L, ChapterNavigator.nextChapterStart(chapters, 5_000L))
    }

    @Test fun `next chapter from last chapter returns null`() {
        assertNull(ChapterNavigator.nextChapterStart(chapters, 90_000L))
    }

    @Test fun `prev chapter well into Ch 2 returns Ch 2 start`() {
        assertEquals(20_000L, ChapterNavigator.prevChapterStart(chapters, 40_000L))
    }

    @Test fun `prev chapter within 3s of Ch 2 start goes to Intro start`() {
        assertEquals(0L, ChapterNavigator.prevChapterStart(chapters, 21_000L))
    }

    @Test fun `prev chapter from well into Intro rewinds to Intro start`() {
        assertEquals(0L, ChapterNavigator.prevChapterStart(chapters, 5_000L))
    }

    @Test fun `prev chapter within threshold at first chapter returns null`() {
        assertNull(ChapterNavigator.prevChapterStart(chapters, 1_000L))
    }

    @Test fun `empty chapter list returns null for all`() {
        assertNull(ChapterNavigator.currentChapter(emptyList(), 0L))
        assertNull(ChapterNavigator.nextChapterStart(emptyList(), 0L))
        assertNull(ChapterNavigator.prevChapterStart(emptyList(), 0L))
    }
}
