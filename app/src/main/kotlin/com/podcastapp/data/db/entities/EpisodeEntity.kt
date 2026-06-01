package com.podcastapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val audioUrl: String,
    val podcastFeedUrl: String,
    val title: String,
    val pubDate: Long,
    val durationSeconds: Int,
    val chaptersUrl: String?,
    val downloadPath: String?,
    val downloadStatus: String = "NONE"
)
