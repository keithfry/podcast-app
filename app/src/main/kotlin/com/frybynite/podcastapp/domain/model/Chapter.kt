package com.frybynite.podcastapp.domain.model

data class Chapter(
    val id: Long = 0,
    val episodeAudioUrl: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String,
    val url: String?
)
