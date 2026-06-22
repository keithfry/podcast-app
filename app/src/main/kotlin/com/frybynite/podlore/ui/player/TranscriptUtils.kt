package com.frybynite.podlore.ui.player

import com.frybynite.podlore.domain.model.TranscriptSegment

fun segmentsForChapter(
    segments: List<TranscriptSegment>,
    chapterStartSec: Float,
    nextChapterStartSec: Float
): List<TranscriptSegment> =
    segments.filter { it.startTimeSec >= chapterStartSec && it.startTimeSec < nextChapterStartSec }
