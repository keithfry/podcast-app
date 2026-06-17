package com.frybynite.podcastapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.frybynite.podcastapp.data.db.dao.ChapterDao
import com.frybynite.podcastapp.data.db.dao.DeepDiveDao
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.db.entities.ChapterEntity
import com.frybynite.podcastapp.data.db.entities.DeepDiveEntity
import com.frybynite.podcastapp.data.db.entities.EpisodeEntity
import com.frybynite.podcastapp.data.db.entities.PodcastEntity

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, ChapterEntity::class, DeepDiveEntity::class],
    version = 6,
    exportSchema = false
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun chapterDao(): ChapterDao
    abstract fun deepDiveDao(): DeepDiveDao
}
