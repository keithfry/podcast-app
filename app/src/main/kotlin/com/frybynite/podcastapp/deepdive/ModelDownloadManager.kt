package com.frybynite.podcastapp.deepdive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelDownloadState {
    data object Idle : ModelDownloadState()
    data class Downloading(val progress: Float) : ModelDownloadState()
    data object Complete : ModelDownloadState()
    data class Failed(val error: String) : ModelDownloadState()
}

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    companion object {
        const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-gpu-int4/float16/latest/model.task"
    }

    init {
        NotificationHelper.createChannel(context)
    }

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state

    suspend fun downloadModel(pendingUrl: String) = withContext(Dispatchers.IO) {
        val dest = context.filesDir.resolve("models/gemma-2b-it-int4.bin")
        dest.parentFile?.mkdirs()
        _state.value = ModelDownloadState.Downloading(0f)
        runCatching {
            val response = client.newCall(Request.Builder().url(MODEL_URL).build()).execute()
            val body = response.body ?: error("Empty response body")
            val total = body.contentLength()
            val tmp = File("${dest.absolutePath}.tmp")
            var downloaded = 0L
            tmp.outputStream().use { out ->
                body.byteStream().use { src ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (src.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) _state.value = ModelDownloadState.Downloading(downloaded.toFloat() / total)
                    }
                }
            }
            tmp.renameTo(dest)
            _state.value = ModelDownloadState.Complete
            NotificationHelper.postReady(context, pendingUrl)
        }.onFailure { e ->
            _state.value = ModelDownloadState.Failed(e.message ?: "Download failed")
        }
    }
}
