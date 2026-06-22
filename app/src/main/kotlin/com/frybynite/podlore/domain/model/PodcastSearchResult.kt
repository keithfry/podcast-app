package com.frybynite.podlore.domain.model

data class PodcastSearchResult(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val description: String?,
    val isSubscribed: Boolean = false,
)
