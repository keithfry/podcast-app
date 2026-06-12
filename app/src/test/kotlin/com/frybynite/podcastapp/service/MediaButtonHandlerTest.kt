package com.frybynite.podcastapp.service

import com.frybynite.podcastapp.domain.model.Chapter
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaButtonHandlerTest {

    private val chapters = listOf(
        Chapter(episodeAudioUrl = "u", startTimeMs = 0,      endTimeMs = 20_000,  title = "Intro", url = null),
        Chapter(episodeAudioUrl = "u", startTimeMs = 20_000, endTimeMs = 60_000,  title = "Ch 2",  url = null),
        Chapter(episodeAudioUrl = "u", startTimeMs = 60_000, endTimeMs = 120_000, title = "Ch 3",  url = null)
    )

    // --- NEXT during normal playback ---

    @Test fun `next during normal playback seeks to next chapter`() {
        assertEquals(20_000L, MediaButtonHandler.handleNext(chapters, 5_000L, isDeepDive = false, durationMs = 120_000L))
    }

    @Test fun `next at last chapter during normal playback returns null`() {
        assertNull(MediaButtonHandler.handleNext(chapters, 90_000L, isDeepDive = false, durationMs = 120_000L))
    }

    @Test fun `next with empty chapters during normal playback returns null`() {
        assertNull(MediaButtonHandler.handleNext(emptyList(), 5_000L, isDeepDive = false, durationMs = 120_000L))
    }

    // --- PREV during normal playback ---

    @Test fun `prev well into chapter during normal playback rewinds to chapter start`() {
        assertEquals(20_000L, MediaButtonHandler.handlePrev(chapters, 40_000L, isDeepDive = false))
    }

    @Test fun `prev within 3s of chapter start during normal playback goes to previous chapter`() {
        assertEquals(0L, MediaButtonHandler.handlePrev(chapters, 21_000L, isDeepDive = false))
    }

    @Test fun `prev at first chapter within threshold during normal playback returns null`() {
        assertNull(MediaButtonHandler.handlePrev(chapters, 1_000L, isDeepDive = false))
    }

    // --- NEXT during deep dive ---

    @Test fun `next during deep dive seeks to near end of TTS item`() {
        val duration = 30_000L
        assertEquals(duration - 1, MediaButtonHandler.handleNext(chapters, 5_000L, isDeepDive = true, durationMs = duration))
    }

    @Test fun `next during deep dive ignores chapter list`() {
        val duration = 15_000L
        assertEquals(duration - 1, MediaButtonHandler.handleNext(emptyList(), 0L, isDeepDive = true, durationMs = duration))
    }

    // --- PREV during deep dive ---

    @Test fun `prev during deep dive restarts TTS from beginning`() {
        assertEquals(0L, MediaButtonHandler.handlePrev(chapters, 10_000L, isDeepDive = true))
    }

    @Test fun `prev during deep dive ignores chapter list`() {
        assertEquals(0L, MediaButtonHandler.handlePrev(emptyList(), 5_000L, isDeepDive = true))
    }
}
