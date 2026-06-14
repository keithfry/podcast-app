package com.frybynite.podcastapp.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.frybynite.podcastapp.data.db.PodcastDatabase
import com.frybynite.podcastapp.data.db.entities.DeepDiveEntity
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
class DeepDiveDaoTest {

    private lateinit var db: PodcastDatabase
    private lateinit var dao: DeepDiveDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PodcastDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.deepDiveDao()
        runTest {
            db.podcastDao().upsert(
                PodcastEntity("https://feed.com", "Feed", "Author", "Desc", null, 0L)
            )
            db.episodeDao().upsertAll(listOf(
                EpisodeEntity("https://ep1.mp3", "https://feed.com", "Ep1", 0L, 120, null, null),
                EpisodeEntity("https://ep2.mp3", "https://feed.com", "Ep2", 0L, 120, null, null)
            ))
        }
    }

    @After fun tearDown() { db.close() }

    private fun entity(
        episodeUrl: String = "https://ep1.mp3",
        chapterUrl: String = "https://chapter.com",
        filePath: String = "/cache/dd.wav",
        summaryText: String = "Summary"
    ) = DeepDiveEntity(
        episodeAudioUrl = episodeUrl,
        chapterUrl = chapterUrl,
        filePath = filePath,
        summaryText = summaryText,
        createdAt = 1000L
    )

    @Test fun upsert_insertsRow() = runTest {
        dao.upsert(entity())
        val result = dao.get("https://ep1.mp3", "https://chapter.com")
        assertEquals("Summary", result?.summaryText)
    }

    @Test fun get_returnsNullWhenMissing() = runTest {
        assertNull(dao.get("https://ep1.mp3", "https://chapter.com"))
    }

    @Test fun upsert_replacesOnCompositePkConflict() = runTest {
        dao.upsert(entity(summaryText = "Old", filePath = "/old.wav"))
        dao.upsert(entity(summaryText = "New", filePath = "/new.wav"))
        val result = dao.get("https://ep1.mp3", "https://chapter.com")!!
        assertEquals("New", result.summaryText)
        assertEquals("/new.wav", result.filePath)
    }

    @Test fun deleteForEpisode_removesOnlyTargetEpisode() = runTest {
        dao.upsert(entity(episodeUrl = "https://ep1.mp3", chapterUrl = "https://ch1.com"))
        dao.upsert(entity(episodeUrl = "https://ep2.mp3", chapterUrl = "https://ch1.com"))
        dao.deleteForEpisode("https://ep1.mp3")
        assertNull(dao.get("https://ep1.mp3", "https://ch1.com"))
        assertEquals("Summary", dao.get("https://ep2.mp3", "https://ch1.com")?.summaryText)
    }

    @Test fun flowForEpisode_emitsOnUpsert() = runTest {
        dao.flowForEpisode("https://ep1.mp3").test {
            assertEquals(0, awaitItem().size)
            dao.upsert(entity(chapterUrl = "https://ch1.com"))
            assertEquals(1, awaitItem().size)
            dao.upsert(entity(chapterUrl = "https://ch2.com"))
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun flowForEpisode_excludesOtherEpisodes() = runTest {
        dao.upsert(entity(episodeUrl = "https://ep2.mp3"))
        dao.flowForEpisode("https://ep1.mp3").test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
