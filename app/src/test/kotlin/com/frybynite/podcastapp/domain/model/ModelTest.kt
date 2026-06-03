package com.frybynite.podcastapp.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelTest {
    @Test fun `chapter contains url`() {
        val c = Chapter(episodeAudioUrl = "http://ep.mp3", startTimeMs = 0, endTimeMs = 5000,
            title = "Intro", url = "https://example.com")
        assertEquals("https://example.com", c.url)
    }

    @Test fun `chapter url is nullable`() {
        val c = Chapter(episodeAudioUrl = "http://ep.mp3", startTimeMs = 0, endTimeMs = 5000,
            title = "Intro", url = null)
        assertNull(c.url)
    }

    @Test fun `episode download status defaults to NONE`() {
        val e = Episode(audioUrl = "http://ep.mp3", podcastFeedUrl = "http://feed.xml",
            title = "Ep 1", pubDate = 0L, durationSeconds = 60, chaptersUrl = null)
        assertEquals(DownloadStatus.NONE, e.downloadStatus)
    }

    @Test fun `podcast has feed url as identifier`() {
        val p = Podcast(feedUrl = "http://feed.xml", title = "Test", author = "Author",
            description = "Desc", imageUrl = null)
        assertEquals("http://feed.xml", p.feedUrl)
    }
}
