package com.frybynite.podlore.data.network

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

    @Test fun `parses podcast transcript tag url`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
     xmlns:podcast="https://podcastindex.org/namespace/1.0">
  <channel>
    <title>Test Podcast</title>
    <link>https://example.com</link>
    <item>
      <title>Episode 1</title>
      <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="12345"/>
      <pubDate>Mon, 01 Jan 2024 00:00:00 +0000</pubDate>
      <itunes:duration>300</itunes:duration>
      <podcast:transcript url="https://example.com/ep1-2024-01-01.transcript.json" type="application/json"/>
    </item>
  </channel>
</rss>"""
        val feed = RssParser().parse(xml)
        assertEquals("https://example.com/ep1-2024-01-01.transcript.json", feed.episodes[0].transcriptUrl)
    }

    @Test fun `transcriptUrl is null when tag absent`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
     xmlns:podcast="https://podcastindex.org/namespace/1.0">
  <channel>
    <title>Test Podcast</title>
    <link>https://example.com</link>
    <item>
      <title>Episode 1</title>
      <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="12345"/>
      <pubDate>Mon, 01 Jan 2024 00:00:00 +0000</pubDate>
      <itunes:duration>300</itunes:duration>
    </item>
  </channel>
</rss>"""
        val feed = RssParser().parse(xml)
        assertNull(feed.episodes[0].transcriptUrl)
    }
}
