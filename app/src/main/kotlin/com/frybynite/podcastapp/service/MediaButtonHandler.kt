package com.frybynite.podcastapp.service

import com.frybynite.podcastapp.domain.model.Chapter

object MediaButtonHandler {

    fun handleNext(
        chapters: List<Chapter>,
        positionMs: Long,
        isDeepDive: Boolean,
        durationMs: Long
    ): Long? = if (isDeepDive) {
        durationMs - 1
    } else {
        ChapterNavigator.nextChapterStart(chapters, positionMs)
    }

    fun handlePrev(
        chapters: List<Chapter>,
        positionMs: Long,
        isDeepDive: Boolean
    ): Long? = if (isDeepDive) {
        0L
    } else {
        ChapterNavigator.prevChapterStart(chapters, positionMs)
    }
}
