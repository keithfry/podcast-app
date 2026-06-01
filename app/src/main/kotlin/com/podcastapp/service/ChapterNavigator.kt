package com.podcastapp.service

import com.podcastapp.domain.model.Chapter

object ChapterNavigator {
    private const val PREV_THRESHOLD_MS = 3_000L

    fun currentChapter(chapters: List<Chapter>, positionMs: Long): Chapter? =
        chapters.lastOrNull { it.startTimeMs <= positionMs }

    fun nextChapterStart(chapters: List<Chapter>, positionMs: Long): Long? {
        val current = currentChapter(chapters, positionMs) ?: return null
        val idx = chapters.indexOf(current)
        return chapters.getOrNull(idx + 1)?.startTimeMs
    }

    fun prevChapterStart(chapters: List<Chapter>, positionMs: Long): Long? {
        val current = currentChapter(chapters, positionMs) ?: return null
        val idx = chapters.indexOf(current)
        val withinThreshold = (positionMs - current.startTimeMs) < PREV_THRESHOLD_MS
        return if (withinThreshold || idx == 0) {
            // Within threshold of current chapter start → go to previous chapter
            // At first chapter → no previous chapter exists
            chapters.getOrNull(idx - 1)?.startTimeMs
        } else {
            current.startTimeMs
        }
    }
}
