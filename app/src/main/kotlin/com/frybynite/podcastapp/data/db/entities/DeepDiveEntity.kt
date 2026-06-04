package com.frybynite.podcastapp.data.db.entities

import androidx.room.Entity

@Entity(tableName = "deep_dives", primaryKeys = ["episodeAudioUrl", "chapterUrl"])
data class DeepDiveEntity(
    val episodeAudioUrl: String,
    val chapterUrl: String,
    val filePath: String,
    val summaryText: String,
    val createdAt: Long
)
