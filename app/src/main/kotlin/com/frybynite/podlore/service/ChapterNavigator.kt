package com.frybynite.podlore.service

import com.frybynite.podlore.domain.model.Chapter

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
        return when {
            withinThreshold && idx == 0 -> null              // already at start of first chapter
            withinThreshold -> chapters[idx - 1].startTimeMs  // go to previous chapter
            else -> current.startTimeMs                        // rewind to start of current chapter
        }
    }
}
