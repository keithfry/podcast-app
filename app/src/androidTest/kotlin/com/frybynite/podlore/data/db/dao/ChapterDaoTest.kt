package com.frybynite.podlore.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.frybynite.podlore.data.db.PodcastDatabase
import com.frybynite.podlore.data.db.entities.ChapterEntity
import com.frybynite.podlore.data.db.entities.EpisodeEntity
import com.frybynite.podlore.data.db.entities.PodcastEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterDaoTest {

    private lateinit var db: PodcastDatabase
    private lateinit var dao: ChapterDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PodcastDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.chapterDao()
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

    private fun chapter(episodeUrl: String, startMs: Long, title: String = "Ch") = ChapterEntity(
        episodeAudioUrl = episodeUrl,
        startTimeMs = startMs,
        endTimeMs = startMs + 60_000L,
        title = title,
        url = null
    )

    @Test fun insertAll_storesRows() = runTest {
        dao.insertAll(listOf(
            chapter("https://ep1.mp3", 0L, "Intro"),
            chapter("https://ep1.mp3", 60_000L, "Main")
        ))
        assertEquals(2, dao.countForEpisode("https://ep1.mp3"))
    }

    @Test fun getForEpisode_orderedByStartTimeMs() = runTest {
        dao.insertAll(listOf(
            chapter("https://ep1.mp3", 60_000L, "Second"),
            chapter("https://ep1.mp3", 0L, "First")
        ))
        dao.getForEpisode("https://ep1.mp3").test {
            val list = awaitItem()
            assertEquals("First", list[0].title)
            assertEquals("Second", list[1].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun deleteForEpisode_removesOnlyTargetEpisode() = runTest {
        dao.insertAll(listOf(
            chapter("https://ep1.mp3", 0L),
            chapter("https://ep2.mp3", 0L)
        ))
        dao.deleteForEpisode("https://ep1.mp3")
        assertEquals(0, dao.countForEpisode("https://ep1.mp3"))
        assertEquals(1, dao.countForEpisode("https://ep2.mp3"))
    }

    @Test fun replaceChaptersForEpisode_removesOldAndInsertsNew() = runTest {
        dao.insertAll(listOf(
            chapter("https://ep1.mp3", 0L, "Old1"),
            chapter("https://ep1.mp3", 60_000L, "Old2")
        ))
        dao.replaceChaptersForEpisode(
            "https://ep1.mp3",
            listOf(chapter("https://ep1.mp3", 0L, "New1"))
        )
        val rows = dao.countForEpisode("https://ep1.mp3")
        assertEquals(1, rows)
        dao.getForEpisode("https://ep1.mp3").test {
            assertEquals("New1", awaitItem().first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun replaceChaptersForEpisode_doesNotAffectOtherEpisode() = runTest {
        dao.insertAll(listOf(
            chapter("https://ep1.mp3", 0L),
            chapter("https://ep2.mp3", 0L)
        ))
        dao.replaceChaptersForEpisode(
            "https://ep1.mp3",
            listOf(chapter("https://ep1.mp3", 0L, "Replaced"))
        )
        assertEquals(1, dao.countForEpisode("https://ep2.mp3"))
    }

    @Test fun getForEpisode_emitsOnReplace() = runTest {
        dao.insertAll(listOf(chapter("https://ep1.mp3", 0L, "Original")))
        dao.getForEpisode("https://ep1.mp3").test {
            assertEquals("Original", awaitItem().first().title)
            dao.replaceChaptersForEpisode(
                "https://ep1.mp3",
                listOf(chapter("https://ep1.mp3", 0L, "Updated"))
            )
            assertEquals("Updated", awaitItem().last().title)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
