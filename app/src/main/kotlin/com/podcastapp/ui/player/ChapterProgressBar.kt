package com.podcastapp.ui.player

import com.podcastapp.domain.model.Chapter
import kotlin.math.abs

internal fun snapToChapter(
    rawMs: Long,
    chapters: List<Chapter>,
    thresholdMs: Long = 10_000L
): Long {
    val nearest = chapters.minByOrNull { abs(it.startTimeMs - rawMs) } ?: return rawMs
    return if (abs(nearest.startTimeMs - rawMs) <= thresholdMs) nearest.startTimeMs else rawMs
}
