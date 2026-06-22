package com.frybynite.podlore.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chapters", indices = [Index(value = ["episodeAudioUrl"])])
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeAudioUrl: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String,
    val url: String?
)
