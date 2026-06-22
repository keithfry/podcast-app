package com.frybynite.podlore.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.frybynite.podlore.data.db.dao.ChapterDao
import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.db.entities.ChapterEntity
import com.frybynite.podlore.data.db.entities.DeepDiveEntity
import com.frybynite.podlore.data.db.entities.EpisodeEntity
import com.frybynite.podlore.data.db.entities.PodcastEntity

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, ChapterEntity::class, DeepDiveEntity::class],
    version = 7,
    exportSchema = false
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun chapterDao(): ChapterDao
    abstract fun deepDiveDao(): DeepDiveDao
}
