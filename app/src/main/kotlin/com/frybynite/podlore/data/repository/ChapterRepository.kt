package com.frybynite.podlore.data.repository

import com.frybynite.podlore.data.db.dao.ChapterDao
import com.frybynite.podlore.data.db.entities.ChapterEntity
import com.frybynite.podlore.data.network.FeedApi
import com.frybynite.podlore.data.network.toDomainChapters
import com.frybynite.podlore.domain.model.Chapter
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChapterRepo"

@Singleton
class ChapterRepository @Inject constructor(
    private val feedApi: FeedApi,
    private val chapterDao: ChapterDao
) {
    fun chaptersForEpisode(audioUrl: String): Flow<List<Chapter>> =
        chapterDao.getForEpisode(audioUrl).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun fetchAndCacheChapters(audioUrl: String, chaptersUrl: String) {
        val cached = chapterDao.countForEpisode(audioUrl)
        if (cached > 0) {
            Log.d(TAG, "fetchAndCacheChapters: cache hit count=$cached for $audioUrl")
            return
        }
        Log.i(TAG, "fetchAndCacheChapters: fetching from $chaptersUrl")
        val response = feedApi.fetchChapters(chaptersUrl)
        val chapters = response.toDomainChapters(audioUrl).map { it.toEntity() }
        Log.i(TAG, "fetchAndCacheChapters: saving count=${chapters.size} chapters for $audioUrl")
        chapterDao.replaceChaptersForEpisode(audioUrl, chapters)
    }
}

fun ChapterEntity.toDomain() = Chapter(id, episodeAudioUrl, startTimeMs, endTimeMs, title, url)
fun Chapter.toEntity() = ChapterEntity(id, episodeAudioUrl, startTimeMs, endTimeMs, title, url)
