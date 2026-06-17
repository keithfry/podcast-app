package com.frybynite.podcastapp.domain.model

data class Episode(
    val audioUrl: String,
    val podcastFeedUrl: String,
    val title: String,
    val pubDate: Long,
    val durationSeconds: Int,
    val chaptersUrl: String?,
    val transcriptUrl: String? = null,
    val imageUrl: String? = null,
    val downloadPath: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val lastPositionMs: Long = 0L,
    val isHeard: Boolean = false
)

enum class DownloadStatus { NONE, QUEUED, DOWNLOADING, DONE }
