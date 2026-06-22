package com.frybynite.podlore.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.frybynite.podlore.data.db.dao.EpisodeDao
import com.frybynite.podlore.data.db.dao.PodcastDao
import com.frybynite.podlore.data.storage.CacheStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val cacheStorage: CacheStorage,
    private val okHttp: OkHttpClient
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return Result.failure()
        episodeDao.updateDownloadStatus(audioUrl, null, "DOWNLOADING")
        return try {
            val file = downloadToFile(audioUrl)
            episodeDao.updateDownloadStatus(audioUrl, file.absolutePath, "DONE")
            Result.success()
        } catch (e: Exception) {
            episodeDao.updateDownloadStatus(audioUrl, null, "NONE")
            Result.retry()
        }
    }

    private suspend fun downloadToFile(audioUrl: String): File = withContext(Dispatchers.IO) {
        val episode = episodeDao.getByAudioUrl(audioUrl) ?: throw Exception("Unknown episode")
        val podcastTitle = podcastDao.getByUrl(episode.podcastFeedUrl)?.title ?: "untitled"
        val finalFile = cacheStorage.mainAudioFile(
            episode.podcastFeedUrl, podcastTitle, audioUrl, episode.title
        )
        finalFile.parentFile?.mkdirs()
        val tmpFile = File(finalFile.parentFile, "${finalFile.name}.tmp")

        val request = Request.Builder().url(audioUrl).build()
        val response = okHttp.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val totalBytes = response.body?.contentLength() ?: -1L
        try {
            response.body?.byteStream()?.use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesWritten = 0L
                    var lastReportedPct = -1
                    var n = input.read(buffer)
                    while (n >= 0) {
                        output.write(buffer, 0, n)
                        bytesWritten += n
                        if (totalBytes > 0) {
                            val pct = ((bytesWritten * 100) / totalBytes).toInt()
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                setProgress(workDataOf(
                                    KEY_AUDIO_URL to audioUrl,
                                    KEY_PROGRESS to bytesWritten.toFloat() / totalBytes
                                ))
                            }
                        }
                        n = input.read(buffer)
                    }
                }
            } ?: throw Exception("Empty body")
            if (!tmpFile.renameTo(finalFile)) throw Exception("Rename failed")
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
        finalFile
    }

    companion object {
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_PROGRESS = "progress"
        const val TAG = "episode_download"

        fun buildRequest(audioUrl: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_AUDIO_URL to audioUrl))
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
    }
}
