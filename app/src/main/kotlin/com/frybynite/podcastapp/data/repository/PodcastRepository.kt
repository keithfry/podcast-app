package com.frybynite.podcastapp.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.db.entities.EpisodeEntity
import com.frybynite.podcastapp.data.db.entities.PodcastEntity
import com.frybynite.podcastapp.data.download.DownloadWorker
import com.frybynite.podcastapp.data.network.FeedApi
import com.frybynite.podcastapp.data.network.RssParser
import com.frybynite.podcastapp.data.storage.CacheStorage
import com.frybynite.podcastapp.domain.model.DownloadStatus
import com.frybynite.podcastapp.domain.model.Episode
import com.frybynite.podcastapp.domain.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val feedApi: FeedApi,
    private val rssParser: RssParser,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val workManager: WorkManager,
    private val cacheStorage: CacheStorage
) {
    val podcasts: Flow<List<Podcast>> = podcastDao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun addPodcast(feedUrl: String) {
        val xml = feedApi.fetchXml(feedUrl)
        val parsed = rssParser.parse(xml)
        val podcast = parsed.podcast.copy(feedUrl = feedUrl, lastUpdated = System.currentTimeMillis())
        podcastDao.upsert(podcast.toEntity())
        episodeDao.upsertFromFeed(parsed.episodes.map { it.toEntity(feedUrl) })
    }

    suspend fun refreshPodcast(feedUrl: String) = addPodcast(feedUrl)

    fun episodesForPodcast(feedUrl: String): Flow<List<Episode>> =
        episodeDao.getForPodcast(feedUrl).map { list -> list.map { it.toDomain() } }

    suspend fun removePodcast(feedUrl: String) {
        podcastDao.getByUrl(feedUrl)?.let { podcastDao.delete(it) }
    }

    fun downloadEpisode(audioUrl: String) {
        workManager.enqueueUniqueWork(
            "download_$audioUrl",
            ExistingWorkPolicy.KEEP,
            DownloadWorker.buildRequest(audioUrl)
        )
    }

    suspend fun markEpisodeHeard(audioUrl: String) {
        episodeDao.markHeard(audioUrl)
        cleanupEpisodeFiles(audioUrl)
    }

    suspend fun setEpisodeHeard(audioUrl: String, isHeard: Boolean) {
        if (isHeard) {
            episodeDao.markHeard(audioUrl)
            cleanupEpisodeFiles(audioUrl)
        } else {
            episodeDao.markUnheard(audioUrl)
        }
    }

    private suspend fun cleanupEpisodeFiles(audioUrl: String) {
        val entity = episodeDao.getByAudioUrl(audioUrl) ?: return
        entity.downloadPath?.let { File(it).delete() }
        val podcastTitle = podcastDao.getByUrl(entity.podcastFeedUrl)?.title ?: return
        cacheStorage.episodeDir(entity.podcastFeedUrl, podcastTitle, audioUrl, entity.title)
            .deleteRecursively()
        episodeDao.updateDownloadStatus(audioUrl, null, "NONE")
    }
}

// Mapping extensions
fun PodcastEntity.toDomain() = Podcast(feedUrl, title, author, description, imageUrl, lastUpdated)
fun Podcast.toEntity() = PodcastEntity(feedUrl, title, author, description, imageUrl, lastUpdated)

fun EpisodeEntity.toDomain() = Episode(
    audioUrl = audioUrl, podcastFeedUrl = podcastFeedUrl, title = title,
    pubDate = pubDate, durationSeconds = durationSeconds, chaptersUrl = chaptersUrl,
    imageUrl = imageUrl, downloadPath = downloadPath,
    downloadStatus = DownloadStatus.valueOf(downloadStatus), lastPositionMs = lastPositionMs,
    isHeard = isHeard
)
fun Episode.toEntity(feedUrl: String) = EpisodeEntity(
    audioUrl = audioUrl, podcastFeedUrl = feedUrl, title = title, pubDate = pubDate,
    durationSeconds = durationSeconds, chaptersUrl = chaptersUrl, imageUrl = imageUrl,
    downloadPath = downloadPath, downloadStatus = downloadStatus.name
)
