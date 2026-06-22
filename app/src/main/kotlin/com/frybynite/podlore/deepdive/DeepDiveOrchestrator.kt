package com.frybynite.podlore.deepdive

import android.util.Log
import com.frybynite.podlore.data.db.dao.DeepDiveDao
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.db.entities.DeepDiveEntity
import com.frybynite.podlore.data.storage.CacheStorage
import com.frybynite.podlore.ui.player.DeepDiveStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class DeepDiveOrchestrator @Inject constructor(
    private val fetcher: UrlContentFetcher,
    private val summarizer: TextSummarizer,
    private val tts: TtsSynthesizer,
    private val client: OkHttpClient,
    private val cacheStorage: CacheStorage,
    private val deepDiveDao: DeepDiveDao,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao
) {
    suspend fun isCached(chapterUrl: String, episodeAudioUrl: String?): Boolean {
        if (episodeAudioUrl == null) return false
        val row = deepDiveDao.get(episodeAudioUrl, chapterUrl)
        val fileExists = row?.let { File(it.filePath).exists() } ?: false
        Log.i("DeepDive", "isCached: chapterUrl=$chapterUrl episodeUrl=$episodeAudioUrl row=${row?.filePath} fileExists=$fileExists")
        return fileExists
    }

    suspend fun process(
        chapterUrl: String,
        episodeAudioUrl: String? = null,
        chapterTitle: String? = null,
        onStep: (DeepDiveStep) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        Log.i("DeepDive", "process: start url=$chapterUrl episode=$episodeAudioUrl")

        // 1. Cache hit: row + file present -> reuse, skip all work. Only needs the episode URL.
        if (episodeAudioUrl != null) {
            val row = deepDiveDao.get(episodeAudioUrl, chapterUrl)
            if (row != null && File(row.filePath).exists()) {
                Log.i("DeepDive", "process: cache HIT ${row.filePath}")
                return@withContext File(row.filePath)
            }
        }

        // Resolve episode + podcast context for cache paths. If unavailable, generate without caching.
        val episode = episodeAudioUrl?.let { episodeDao.getByAudioUrl(it) }
        val podcastTitle = episode?.let { podcastDao.getByUrl(it.podcastFeedUrl)?.title } ?: "untitled"
        val canCache = episode != null && episodeAudioUrl != null

        // 2. Miss: fetch (reusing cached metadata.json for any prior summary).
        onStep(DeepDiveStep.FETCHING)
        val existingSummary = if (canCache)
            fetchExistingSummary(episodeAudioUrl!!, episode!!.podcastFeedUrl, podcastTitle, episode.title, chapterUrl)
        else null
        val text = fetcher.fetch(chapterUrl)
        onStep(DeepDiveStep.SUMMARIZING)
        val summary = summarizer.summarize(text, existingSummary)
        onStep(DeepDiveStep.SYNTHESIZING)
        val tmpFile = tts.synthesizeToFile(summary)

        // 3. If we can cache, move the temp file into the episode dir and record it.
        if (!canCache) {
            Log.i("DeepDive", "process: no episode context, returning temp file (uncached)")
            return@withContext tmpFile
        }
        val target = cacheStorage.deepDiveFile(
            episode!!.podcastFeedUrl, podcastTitle, episodeAudioUrl!!, episode.title, chapterUrl, chapterTitle
        )
        target.parentFile?.mkdirs()
        if (!tmpFile.renameTo(target)) {
            tmpFile.copyTo(target, overwrite = true); tmpFile.delete()
        }
        deepDiveDao.upsert(
            DeepDiveEntity(episodeAudioUrl, chapterUrl, target.absolutePath, summary, System.currentTimeMillis())
        )
        Log.i("DeepDive", "process: cached ${target.absolutePath}")
        target
    }

    /** Reads the cached metadata.json if present; otherwise fetches the .json sidecar once and saves it. */
    private fun fetchExistingSummary(
        episodeAudioUrl: String, feedUrl: String, podcastTitle: String, episodeTitle: String, chapterUrl: String
    ): String? = runCatching {
        val metaFile = cacheStorage.metadataFile(feedUrl, podcastTitle, episodeAudioUrl, episodeTitle)
        val body: String = if (metaFile.exists()) {
            metaFile.readText()
        } else {
            val jsonUrl = episodeAudioUrl.replace(Regex("\\.(mp3|m4a|ogg)$"), ".json")
            val response = client.newCall(Request.Builder().url(jsonUrl).build()).execute()
            val fetched = response.use { it.body?.string() } ?: return@runCatching null
            metaFile.parentFile?.mkdirs()
            metaFile.writeText(fetched)
            fetched
        }
        val items = JSONObject(body).getJSONArray("items")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("link") == chapterUrl) {
                return@runCatching item.optString("summary").takeIf { it.isNotEmpty() }
            }
        }
        null
    }.onFailure { e ->
        Log.w("DeepDive", "Could not load episode JSON context", e)
    }.getOrNull()
}
