package com.frybynite.podcastapp.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.frybynite.podcastapp.data.db.dao.EpisodeDao
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
        val request = Request.Builder().url(audioUrl).build()
        val response = okHttp.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val file = File(applicationContext.filesDir, "${audioUrl.hashCode()}.mp3")
        response.body?.byteStream()?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("Empty body")
        file
    }

    companion object {
        const val KEY_AUDIO_URL = "audio_url"

        fun buildRequest(audioUrl: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_AUDIO_URL to audioUrl))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
    }
}
