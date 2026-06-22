package com.frybynite.podlore.domain.model

data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    val lastUpdated: Long = 0L
)
