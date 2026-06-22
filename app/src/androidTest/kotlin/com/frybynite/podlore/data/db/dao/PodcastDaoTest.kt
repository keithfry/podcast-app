package com.frybynite.podlore.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.frybynite.podlore.data.db.PodcastDatabase
import com.frybynite.podlore.data.db.entities.PodcastEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastDaoTest {

    private lateinit var db: PodcastDatabase
    private lateinit var dao: PodcastDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PodcastDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.podcastDao()
    }

    @After fun tearDown() { db.close() }

    private fun podcast(feedUrl: String, lastUpdated: Long = 0L) = PodcastEntity(
        feedUrl = feedUrl,
        title = "Title $feedUrl",
        author = "Author",
        description = "Desc",
        imageUrl = null,
        lastUpdated = lastUpdated
    )

    @Test fun upsert_insertsNewRow() = runTest {
        dao.upsert(podcast("https://feed1.com"))
        assertEquals("https://feed1.com", dao.getByUrl("https://feed1.com")?.feedUrl)
    }

    @Test fun upsert_replacesExistingRow() = runTest {
        dao.upsert(podcast("https://feed1.com").copy(title = "Old"))
        dao.upsert(podcast("https://feed1.com").copy(title = "New"))
        assertEquals("New", dao.getByUrl("https://feed1.com")?.title)
    }

    @Test fun getByUrl_returnsNullWhenMissing() = runTest {
        assertNull(dao.getByUrl("https://not-there.com"))
    }

    @Test fun delete_removesRow() = runTest {
        val p = podcast("https://feed1.com")
        dao.upsert(p)
        dao.delete(p)
        assertNull(dao.getByUrl("https://feed1.com"))
    }

    @Test fun getAll_orderedByLastUpdatedDesc() = runTest {
        dao.upsert(podcast("https://older.com", lastUpdated = 1000L))
        dao.upsert(podcast("https://newer.com", lastUpdated = 2000L))
        dao.getAll().test {
            val list = awaitItem()
            assertEquals("https://newer.com", list[0].feedUrl)
            assertEquals("https://older.com", list[1].feedUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun getAll_emitsOnInsert() = runTest {
        dao.getAll().test {
            assertEquals(0, awaitItem().size)
            dao.upsert(podcast("https://feed1.com"))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
