package com.frybynite.podlore.data.network

import com.frybynite.podlore.domain.model.TranscriptSegment
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TranscriptResponse(
    @Json(name = "version") val version: String = "",
    @Json(name = "segments") val segments: List<TranscriptSegmentDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TranscriptSegmentDto(
    @Json(name = "startTime") val startTime: Float = 0f,
    @Json(name = "endTime") val endTime: Float = 0f,
    @Json(name = "text") val text: String = ""
)

fun TranscriptResponse.toSegments(): List<TranscriptSegment> =
    segments.map { dto ->
        TranscriptSegment(
            startTimeSec = dto.startTime,
            endTimeSec = dto.endTime,
            text = dto.text
        )
    }
