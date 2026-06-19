package com.frybynite.podcastapp.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.frybynite.podcastapp.data.db.PodcastDatabase
import com.frybynite.podcastapp.data.db.entities.EpisodeEntity
import com.frybynite.podcastapp.data.db.entities.PodcastEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeDaoTest {

    private lateinit var db: PodcastDatabase
    private lateinit var dao: EpisodeDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PodcastDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.episodeDao()
        runTest {
            db.podcastDao().upsert(
                PodcastEntity("https://feed.com", "Feed", "Author", "Desc", null, 0L)
            )
        }
    }

    @After fun tearDown() { db.close() }

    private fun episode(
        audioUrl: String,
        feedUrl: String = "https://feed.com",
        pubDate: Long = 0L
    ) = EpisodeEntity(
        audioUrl = audioUrl,
        podcastFeedUrl = feedUrl,
        title = "Title $audioUrl",
        pubDate = pubDate,
        durationSeconds = 120,
        chaptersUrl = null,
        downloadPath = null
    )

    @Test fun upsertAll_insertsRows() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3"), episode("https://ep2.mp3")))
        assertEquals("https://ep1.mp3", dao.getByAudioUrl("https://ep1.mp3")?.audioUrl)
        assertEquals("https://ep2.mp3", dao.getByAudioUrl("https://ep2.mp3")?.audioUrl)
    }

    @Test fun upsertAll_replacesExistingRow() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3").copy(title = "Old")))
        dao.upsertAll(listOf(episode("https://ep1.mp3").copy(title = "New")))
        assertEquals("New", dao.getByAudioUrl("https://ep1.mp3")?.title)
    }

    @Test fun getByAudioUrl_returnsNullWhenMissing() = runTest {
        assertNull(dao.getByAudioUrl("https://not-there.mp3"))
    }

    @Test fun getForPodcast_orderedByPubDateDesc() = runTest {
        dao.upsertAll(listOf(
            episode("https://old.mp3", pubDate = 1000L),
            episode("https://new.mp3", pubDate = 2000L)
        ))
        dao.getForPodcast("https://feed.com").test {
            val list = awaitItem()
            assertEquals("https://new.mp3", list[0].audioUrl)
            assertEquals("https://old.mp3", list[1].audioUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun getForPodcast_excludesOtherFeeds() = runTest {
        db.podcastDao().upsert(
            PodcastEntity("https://other.com", "Other", "A", "D", null, 0L)
        )
        dao.upsertAll(listOf(
            episode("https://ep1.mp3", feedUrl = "https://feed.com"),
            episode("https://ep2.mp3", feedUrl = "https://other.com")
        ))
        dao.getForPodcast("https://feed.com").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("https://ep1.mp3", list[0].audioUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun updateLastPosition_persistsValue() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3")))
        dao.updateLastPosition("https://ep1.mp3", 42_000L)
        assertEquals(42_000L, dao.getByAudioUrl("https://ep1.mp3")?.lastPositionMs)
    }

    @Test fun updateDownloadStatus_persistsPathAndStatus() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3")))
        dao.updateDownloadStatus("https://ep1.mp3", "/data/ep1.mp3", "DOWNLOADED")
        val row = dao.getByAudioUrl("https://ep1.mp3")!!
        assertEquals("/data/ep1.mp3", row.downloadPath)
        assertEquals("DOWNLOADED", row.downloadStatus)
    }

    @Test fun getForPodcast_emitsOnInsert() = runTest {
        dao.getForPodcast("https://feed.com").test {
            assertEquals(0, awaitItem().size)
            dao.upsertAll(listOf(episode("https://ep1.mp3")))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun updateIsLiked_persistsTrueValue() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3")))
        dao.updateIsLiked("https://ep1.mp3", true)
        assertEquals(true, dao.getByAudioUrl("https://ep1.mp3")?.isLiked)
    }

    @Test fun updateIsLiked_persistsFalseAfterTrue() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3")))
        dao.updateIsLiked("https://ep1.mp3", true)
        dao.updateIsLiked("https://ep1.mp3", false)
        assertEquals(false, dao.getByAudioUrl("https://ep1.mp3")?.isLiked)
    }

    @Test fun markHeard_setsIsHeardTrue() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3")))
        dao.markHeard("https://ep1.mp3")
        assertEquals(true, dao.getByAudioUrl("https://ep1.mp3")?.isHeard)
    }

    @Test fun markUnheard_setsIsHeardFalseAfterMarkHeard() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3")))
        dao.markHeard("https://ep1.mp3")
        dao.markUnheard("https://ep1.mp3")
        assertEquals(false, dao.getByAudioUrl("https://ep1.mp3")?.isHeard)
    }

    @Test fun upsertFromFeed_preservesIsLiked() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3").copy(isLiked = true)))
        // Simulate feed refresh: entity from feed has isLiked defaulting to false
        dao.upsertFromFeed(listOf(episode("https://ep1.mp3")))
        assertEquals(true, dao.getByAudioUrl("https://ep1.mp3")?.isLiked)
    }

    @Test fun upsertFromFeed_preservesLastPosition() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3").copy(lastPositionMs = 42_000L)))
        dao.upsertFromFeed(listOf(episode("https://ep1.mp3")))
        assertEquals(42_000L, dao.getByAudioUrl("https://ep1.mp3")?.lastPositionMs)
    }

    @Test fun upsertFromFeed_preservesIsHeard() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3")))
        dao.markHeard("https://ep1.mp3")
        dao.upsertFromFeed(listOf(episode("https://ep1.mp3")))
        assertEquals(true, dao.getByAudioUrl("https://ep1.mp3")?.isHeard)
    }

    @Test fun upsertFromFeed_updatesTitle() = runTest {
        dao.upsertAll(listOf(episode("https://ep1.mp3").copy(title = "Old Title")))
        dao.upsertFromFeed(listOf(episode("https://ep1.mp3").copy(title = "New Title")))
        assertEquals("New Title", dao.getByAudioUrl("https://ep1.mp3")?.title)
    }
}
