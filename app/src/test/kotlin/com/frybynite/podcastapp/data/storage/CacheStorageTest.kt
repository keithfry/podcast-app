package com.frybynite.podcastapp.data.storage

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheStorageTest {
    @get:Rule val tmp = TemporaryFolder()
    private var folderCount = 0
    private fun storage() = CacheStorage(tmp.newFolder("podcasts${folderCount++}"))

    @Test fun `slug lowercases and replaces punctuation`() {
        val dir = storage().podcastDir("https://feed.example/rss", "AI & Robotics Daily!")
        assertTrue(dir.name.startsWith("ai-robotics-daily-"))
    }

    @Test fun `empty title slugs to untitled`() {
        val dir = storage().podcastDir("https://feed.example/rss", "   ")
        assertTrue(dir.name.startsWith("untitled-"))
    }

    @Test fun `same title different url produce different dirs`() {
        val s = storage()
        val a = s.podcastDir("https://a.example/rss", "Show")
        val b = s.podcastDir("https://b.example/rss", "Show")
        assertTrue(a.name != b.name)
    }

    @Test fun `hash is deterministic`() {
        val a = storage().podcastDir("https://a.example/rss", "Show").name
        val b = storage().podcastDir("https://a.example/rss", "Show").name
        assertEquals(a, b)
    }

    @Test fun `main audio file keeps extension and nests under episode dir`() {
        val f = storage().mainAudioFile("https://f/rss", "Show", "https://cdn/ep1.m4a", "Ep 1")
        assertEquals("audio.m4a", f.name)
        assertTrue(f.parentFile!!.name.startsWith("ep-1-"))
    }

    @Test fun `main audio defaults to mp3 when url has no extension`() {
        val f = storage().mainAudioFile("https://f/rss", "Show", "https://cdn/stream?id=9", "Ep 1")
        assertEquals("audio.mp3", f.name)
    }

    @Test fun `metadata file is metadata json`() {
        val f = storage().metadataFile("https://f/rss", "Show", "https://cdn/ep1.mp3", "Ep 1")
        assertEquals("metadata.json", f.name)
    }

    @Test fun `deep dive file uses chapter slug and wav extension`() {
        val f = storage().deepDiveFile("https://f/rss", "Show", "https://cdn/ep1.mp3", "Ep 1",
            "https://news/x", "Courts & AI Lawsuits")
        assertTrue(f.name.startsWith("more-courts-ai-lawsuits-"))
        assertTrue(f.name.endsWith(".wav"))
    }

    @Test fun `deep dive file falls back to url slug when title null`() {
        val f = storage().deepDiveFile("https://f/rss", "Show", "https://cdn/ep1.mp3", "Ep 1",
            "https://news/x", null)
        assertTrue(f.name.startsWith("more-"))
        assertTrue(f.name.endsWith(".wav"))
    }
}
