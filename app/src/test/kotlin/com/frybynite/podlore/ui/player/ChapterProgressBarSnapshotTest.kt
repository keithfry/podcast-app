package com.frybynite.podlore.ui.player

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.frybynite.podlore.domain.model.Chapter
import com.frybynite.podlore.ui.theme.PodcastAppTheme
import org.junit.Rule
import org.junit.Test

class ChapterProgressBarSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val chapters = listOf(
        Chapter(1, "ep.mp3", 0L, 300_000L, "Intro", null),
        Chapter(2, "ep.mp3", 300_000L, 900_000L, "Chapter 1", "https://example.com/1"),
        Chapter(3, "ep.mp3", 900_000L, 1_800_000L, "Chapter 2", null),
        Chapter(4, "ep.mp3", 1_800_000L, 3_600_000L, "Outro", null),
    )

    @Test fun `progress at start`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                ChapterProgressBar(
                    positionMs = 0L,
                    durationMs = 3_600_000L,
                    chapters = chapters,
                    onSeek = {}
                )
            }
        }
    }

    @Test fun `progress at midpoint`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                ChapterProgressBar(
                    positionMs = 1_800_000L,
                    durationMs = 3_600_000L,
                    chapters = chapters,
                    onSeek = {}
                )
            }
        }
    }

    @Test fun `progress near end`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                ChapterProgressBar(
                    positionMs = 3_500_000L,
                    durationMs = 3_600_000L,
                    chapters = chapters,
                    onSeek = {}
                )
            }
        }
    }

    @Test fun `no chapters`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                ChapterProgressBar(
                    positionMs = 600_000L,
                    durationMs = 3_600_000L,
                    chapters = emptyList(),
                    onSeek = {}
                )
            }
        }
    }

    @Test fun `zero duration shows empty bar`() {
        paparazzi.snapshot {
            PodcastAppTheme {
                ChapterProgressBar(
                    positionMs = 0L,
                    durationMs = 0L,
                    chapters = emptyList(),
                    onSeek = {}
                )
            }
        }
    }
}
