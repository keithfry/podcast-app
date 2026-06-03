package com.frybynite.podcastapp.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChaptersParserTest {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ChaptersResponse::class.java)

    private val json = """
        {
          "version": "1.2.0",
          "chapters": [
            {"startTime": 0, "endTime": 20, "title": "Introduction"},
            {"startTime": 20, "endTime": 131, "title": "OpenAI News",
             "url": "https://example.com/article"}
          ]
        }
    """.trimIndent()

    @Test fun `parses two chapters`() {
        val response = adapter.fromJson(json)!!
        assertEquals(2, response.chapters.size)
    }

    @Test fun `parses chapter fields`() {
        val chapter = adapter.fromJson(json)!!.chapters[1]
        assertEquals(20, chapter.startTime)
        assertEquals(131, chapter.endTime)
        assertEquals("OpenAI News", chapter.title)
        assertEquals("https://example.com/article", chapter.url)
    }

    @Test fun `url nullable when absent`() {
        val chapter = adapter.fromJson(json)!!.chapters[0]
        assertNull(chapter.url)
    }

    @Test fun `converts to domain chapters with ms`() {
        val response = adapter.fromJson(json)!!
        val domain = response.toDomainChapters("http://ep.mp3")
        assertEquals(20_000L, domain[1].startTimeMs)
        assertEquals(131_000L, domain[1].endTimeMs)
    }

    @Test fun `domain chapters preserve title and url`() {
        val response = adapter.fromJson(json)!!
        val domain = response.toDomainChapters("http://ep.mp3")
        assertEquals("OpenAI News", domain[1].title)
        assertEquals("https://example.com/article", domain[1].url)
    }

    @Test fun `domain chapters have correct episodeAudioUrl`() {
        val response = adapter.fromJson(json)!!
        val domain = response.toDomainChapters("http://ep.mp3")
        assertEquals("http://ep.mp3", domain[0].episodeAudioUrl)
    }
}
