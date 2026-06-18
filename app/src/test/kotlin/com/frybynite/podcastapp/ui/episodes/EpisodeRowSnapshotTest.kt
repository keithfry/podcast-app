package com.frybynite.podcastapp.ui.episodes

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.frybynite.podcastapp.domain.model.DownloadStatus
import com.frybynite.podcastapp.domain.model.Episode
import com.frybynite.podcastapp.ui.theme.PodcastAppTheme
import org.junit.Rule
import org.junit.Test

class EpisodeRowSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val episode = Episode(
        audioUrl = "https://example.com/ep1.mp3",
        podcastFeedUrl = "https://example.com/feed.xml",
        title = "AI and the Future of Work",
        pubDate = 1_700_000_000_000L,
        durationSeconds = 3600,
        chaptersUrl = null,
        downloadStatus = DownloadStatus.NONE
    )

    @Test fun `episode row not downloaded shows play icon`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                EpisodeRow(episode = episode, onClick = {}, onPlayPause = {})
            }
        }
    }

    @Test fun `episode row downloaded shows play icon`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                EpisodeRow(
                    episode = episode.copy(downloadStatus = DownloadStatus.DONE),
                    onClick = {},
                    onPlayPause = {}
                )
            }
        }
    }

    @Test fun `episode row downloading shows progress indicator`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                EpisodeRow(
                    episode = episode.copy(downloadStatus = DownloadStatus.DOWNLOADING),
                    downloadProgress = 0.4f,
                    onClick = {},
                    onPlayPause = {}
                )
            }
        }
    }

    @Test fun `episode row currently playing shows pause icon`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                EpisodeRow(
                    episode = episode.copy(downloadStatus = DownloadStatus.DONE),
                    isCurrentlyPlaying = true,
                    isPlayingActive = true,
                    onClick = {},
                    onPlayPause = {}
                )
            }
        }
    }

    @Test fun `episode row with long title`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                EpisodeRow(
                    episode = episode.copy(
                        title = "A Very Long Episode Title That Describes The Contents Of This Particular Episode In Great Detail"
                    ),
                    onClick = {},
                    onPlayPause = {}
                )
            }
        }
    }
}
