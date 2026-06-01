package com.podcastapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.podcastapp.data.db.dao.ChapterDao
import com.podcastapp.data.db.dao.EpisodeDao
import com.podcastapp.data.db.dao.PodcastDao
import com.podcastapp.data.db.entities.ChapterEntity
import com.podcastapp.data.db.entities.EpisodeEntity
import com.podcastapp.data.db.entities.PodcastEntity

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, ChapterEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun chapterDao(): ChapterDao
}
