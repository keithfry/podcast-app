package com.podcastapp.ui.player

import com.podcastapp.domain.model.Chapter
import org.junit.Test
import kotlin.test.assertEquals

class SnapToChapterTest {
    private val chapters = listOf(
        Chapter(episodeAudioUrl = "u", startTimeMs = 0,      endTimeMs = 20_000,  title = "Intro", url = null),
        Chapter(episodeAudioUrl = "u", startTimeMs = 20_000, endTimeMs = 60_000,  title = "Ch 2",  url = null),
        Chapter(episodeAudioUrl = "u", startTimeMs = 60_000, endTimeMs = 120_000, title = "Ch 3",  url = null)
    )

    @Test fun `within threshold snaps to nearest chapter`() {
        assertEquals(20_000L, snapToChapter(27_000L, chapters))
    }

    @Test fun `beyond threshold returns raw position`() {
        assertEquals(35_000L, snapToChapter(35_000L, chapters))
    }

    @Test fun `snaps to chapter 0 when near start`() {
        assertEquals(0L, snapToChapter(5_000L, chapters))
    }

    @Test fun `snaps to last chapter when near its start`() {
        assertEquals(60_000L, snapToChapter(65_000L, chapters))
    }

    @Test fun `empty chapters returns raw position`() {
        assertEquals(42_000L, snapToChapter(42_000L, emptyList()))
    }

    @Test fun `exact chapter start returns that start`() {
        assertEquals(20_000L, snapToChapter(20_000L, chapters))
    }
}
