package com.podcastapp.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.podcastapp.data.db.dao.EpisodeDao
import com.podcastapp.data.db.dao.PodcastDao
import com.podcastapp.data.db.entities.EpisodeEntity
import com.podcastapp.data.db.entities.PodcastEntity
import com.podcastapp.data.download.DownloadWorker
import com.podcastapp.data.network.FeedApi
import com.podcastapp.data.network.RssParser
import com.podcastapp.domain.model.DownloadStatus
import com.podcastapp.domain.model.Episode
import com.podcastapp.domain.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val feedApi: FeedApi,
    private val rssParser: RssParser,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val workManager: WorkManager
) {
    val podcasts: Flow<List<Podcast>> = podcastDao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun addPodcast(feedUrl: String) {
        val xml = feedApi.fetchXml(feedUrl)
        val parsed = rssParser.parse(xml)
        val podcast = parsed.podcast.copy(feedUrl = feedUrl, lastUpdated = System.currentTimeMillis())
        podcastDao.upsert(podcast.toEntity())
        episodeDao.upsertAll(parsed.episodes.map { it.toEntity(feedUrl) })
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
}

// Mapping extensions
fun PodcastEntity.toDomain() = Podcast(feedUrl, title, author, description, imageUrl, lastUpdated)
fun Podcast.toEntity() = PodcastEntity(feedUrl, title, author, description, imageUrl, lastUpdated)

fun EpisodeEntity.toDomain() = Episode(
    audioUrl = audioUrl, podcastFeedUrl = podcastFeedUrl, title = title,
    pubDate = pubDate, durationSeconds = durationSeconds, chaptersUrl = chaptersUrl,
    downloadPath = downloadPath, downloadStatus = DownloadStatus.valueOf(downloadStatus)
)
fun Episode.toEntity(feedUrl: String) = EpisodeEntity(
    audioUrl = audioUrl, podcastFeedUrl = feedUrl, title = title, pubDate = pubDate,
    durationSeconds = durationSeconds, chaptersUrl = chaptersUrl,
    downloadPath = downloadPath, downloadStatus = downloadStatus.name
)
