package com.frybynite.podcastapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "episodes", indices = [Index(value = ["podcastFeedUrl"])])
data class EpisodeEntity(
    @PrimaryKey val audioUrl: String,
    val podcastFeedUrl: String,
    val title: String,
    val pubDate: Long,
    val durationSeconds: Int,
    val chaptersUrl: String?,
    val imageUrl: String? = null,
    val downloadPath: String?,
    val downloadStatus: String = "NONE",
    val lastPositionMs: Long = 0L
)
