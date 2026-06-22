package com.frybynite.podlore.data.network

import com.frybynite.podlore.domain.model.Chapter
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChaptersResponse(
    @Json(name = "version") val version: String = "",
    @Json(name = "chapters") val chapters: List<ChapterDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChapterDto(
    @Json(name = "startTime") val startTime: Int = 0,
    @Json(name = "endTime") val endTime: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "url") val url: String? = null
)

fun ChaptersResponse.toDomainChapters(episodeAudioUrl: String): List<Chapter> =
    chapters.map { dto ->
        Chapter(
            episodeAudioUrl = episodeAudioUrl,
            startTimeMs = dto.startTime * 1000L,
            endTimeMs = dto.endTime * 1000L,
            title = dto.title,
            url = dto.url
        )
    }
