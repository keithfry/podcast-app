package com.frybynite.podcastapp.domain.model

data class TranscriptSegment(
    val startTimeSec: Float,
    val endTimeSec: Float,
    val text: String
)
