package com.frybynite.podcastapp.data.network

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RssParserTest {
    private val parser = RssParser()
    private val xml = RssParserTest::class.java.classLoader!!
        .getResourceAsStream("test_feed.xml")!!.bufferedReader().readText()

    @Test fun `parses podcast title and author`() {
        val result = parser.parse(xml)
        assertEquals("Test Podcast", result.podcast.title)
        assertEquals("Test Author", result.podcast.author)
    }

    @Test fun `parses two episodes`() {
        val result = parser.parse(xml)
        assertEquals(2, result.episodes.size)
    }

    @Test fun `parses episode fields`() {
        val result = parser.parse(xml)
        val ep = result.episodes.first()
        assertEquals("Episode 1", ep.title)
        assertEquals("https://example.com/ep1.mp3", ep.audioUrl)
        assertEquals(600, ep.durationSeconds) // 00:10:00
    }

    @Test fun `parses chapters url when present`() {
        val result = parser.parse(xml)
        assertEquals("https://example.com/ep1.chapters.json", result.episodes[0].chaptersUrl)
    }

    @Test fun `chapters url null when absent`() {
        val result = parser.parse(xml)
        assertNull(result.episodes[1].chaptersUrl)
    }

    @Test fun `parses episode 2 duration`() {
        val result = parser.parse(xml)
        assertEquals(1230, result.episodes[1].durationSeconds) // 00:20:30
    }
}
