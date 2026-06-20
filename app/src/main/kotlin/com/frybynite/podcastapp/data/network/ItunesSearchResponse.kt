package com.frybynite.podcastapp.data.network

import com.squareup.moshi.Json

data class ItunesSearchResponse(
    @Json(name = "results") val results: List<ItunesSearchItem> = emptyList(),
)

data class ItunesSearchItem(
    @Json(name = "feedUrl") val feedUrl: String? = null,
    @Json(name = "collectionName") val collectionName: String? = null,
    @Json(name = "artistName") val artistName: String? = null,
    @Json(name = "artworkUrl600") val artworkUrl600: String? = null,
    @Json(name = "description") val description: String? = null,
)
