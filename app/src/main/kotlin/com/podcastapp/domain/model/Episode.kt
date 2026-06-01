package com.podcastapp.domain.model

data class Episode(
    val audioUrl: String,
    val podcastFeedUrl: String,
    val title: String,
    val pubDate: Long,
    val durationSeconds: Int,
    val chaptersUrl: String?,
    val downloadPath: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE
)

enum class DownloadStatus { NONE, QUEUED, DOWNLOADING, DONE }
