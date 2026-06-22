package com.frybynite.podlore.domain.model

data class TranscriptSegment(
    val startTimeSec: Float,
    val endTimeSec: Float,
    val text: String
)
