package com.frybynite.podlore.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    val lastUpdated: Long
)
