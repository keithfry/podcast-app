package com.frybynite.podcastapp.ui.player

import com.frybynite.podcastapp.domain.model.TranscriptSegment

fun segmentsForChapter(
    segments: List<TranscriptSegment>,
    chapterStartSec: Float,
    nextChapterStartSec: Float
): List<TranscriptSegment> =
    segments.filter { it.startTimeSec >= chapterStartSec && it.startTimeSec < nextChapterStartSec }
