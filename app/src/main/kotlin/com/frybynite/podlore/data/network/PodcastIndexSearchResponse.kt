package com.frybynite.podlore.data.network

import com.squareup.moshi.Json

data class PodcastIndexSearchResponse(
    @Json(name = "feeds") val feeds: List<PodcastIndexFeed> = emptyList(),
)

data class PodcastIndexFeed(
    @Json(name = "url") val url: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "artwork") val artwork: String? = null,
    @Json(name = "description") val description: String? = null,
)
