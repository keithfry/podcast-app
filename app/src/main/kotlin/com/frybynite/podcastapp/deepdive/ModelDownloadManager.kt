package com.frybynite.podcastapp.deepdive

import android.content.Context
import android.util.Log
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
        const val GPU_MODEL_URL = "https://frybynite-podcast-app-storage.s3.amazonaws.com/models/gemma-gpu-int4.bin"
        const val CPU_MODEL_URL = "https://frybynite-podcast-app-storage.s3.amazonaws.com/models/gemma-cpu-int4.bin"
        const val GPU_MODEL_FILE = "models/gemma-gpu-int4.bin"
        const val CPU_MODEL_FILE = "models/gemma-cpu-int4.bin"
    }

    init {
        NotificationHelper.createChannel(context)
    }

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state

    suspend fun downloadModel(pendingUrl: String) = withContext(Dispatchers.IO) {
        // Download CPU model as reliable baseline
        val cpuSuccess = downloadFile(CPU_MODEL_URL, CPU_MODEL_FILE, pendingUrl, isGpu = false)
        // Also attempt GPU model in background — used if device supports OpenCL
        if (cpuSuccess) {
            Log.i("DeepDive", "CPU model ready, attempting GPU model download")
            downloadFile(GPU_MODEL_URL, GPU_MODEL_FILE, pendingUrl = "", isGpu = true)
        }
    }

    private suspend fun downloadFile(
        url: String,
        relativePath: String,
        pendingUrl: String,
        isGpu: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val dest = context.filesDir.resolve(relativePath)
        dest.parentFile?.mkdirs()
        if (!isGpu || _state.value == ModelDownloadState.Idle) _state.value = ModelDownloadState.Downloading(0f)
        runCatching {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${response.message}")
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
            if (!isGpu) {
                _state.value = ModelDownloadState.Complete
                if (pendingUrl.isNotEmpty()) NotificationHelper.postReady(context, pendingUrl)
            }
        }.onFailure { e ->
            Log.e("DeepDive", "Model download failed (${if (isGpu) "GPU" else "CPU"})", e)
            File("${dest.absolutePath}.tmp").delete()
            if (!isGpu) _state.value = ModelDownloadState.Failed(e.message ?: "Download failed")
        }.isSuccess
    }
}
