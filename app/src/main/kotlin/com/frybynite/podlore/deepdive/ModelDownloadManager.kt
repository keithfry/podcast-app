package com.frybynite.podlore.deepdive

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
        // Primary: LiteRt Qwen3-0.6B (474 MB, no OpenCL required)
        const val LITERTLM_MODEL_URL = LiteRtTextSummarizer.MODEL_URL
        const val LITERTLM_MODEL_FILE = LiteRtTextSummarizer.MODEL_FILE
        // Fallback: Gemma MediaPipe models (require upload to S3 before use)
        const val GPU_MODEL_URL = "https://frybynite-podcast-app-storage.s3.amazonaws.com/models/gemma-2b-it-gpu-int4.bin"
        const val CPU_MODEL_URL = "https://frybynite-podcast-app-storage.s3.amazonaws.com/models/gemma-cpu-int4.bin"
        const val GPU_MODEL_FILE = "models/gemma-2b-it-gpu-int4.bin"
        const val CPU_MODEL_FILE = "models/gemma-cpu-int4.bin"
    }

    init {
        NotificationHelper.createChannel(context)
    }

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state

    suspend fun downloadModel(pendingUrl: String) = withContext(Dispatchers.IO) {
        if (_state.value is ModelDownloadState.Downloading) {
            Log.i("DeepDive", "Download already in progress — ignoring duplicate request")
            return@withContext
        }
        val litertDest = context.filesDir.resolve(LITERTLM_MODEL_FILE)
        val litertReady = litertDest.exists() && litertDest.length() > 400_000_000L
        if (litertReady) {
            Log.i("DeepDive", "LiteRt model already on device — skipping download")
            _state.value = ModelDownloadState.Complete
            if (pendingUrl.isNotEmpty()) NotificationHelper.postReady(context, pendingUrl)
            return@withContext
        }
        // Download primary LiteRt model (Qwen3-0.6B, ~474 MB, no OpenCL required)
        downloadFile(LITERTLM_MODEL_URL, LITERTLM_MODEL_FILE, pendingUrl, isGpu = false)
    }

    private suspend fun downloadFile(
        url: String,
        relativePath: String,
        pendingUrl: String,
        isGpu: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val dest = context.filesDir.resolve(relativePath)
        dest.parentFile?.mkdirs()
        val label = if (isGpu) "GPU" else "CPU"
        Log.i("DeepDive", "Summarizer model download START ($label): $url -> ${dest.absolutePath}")
        _state.value = ModelDownloadState.Downloading(0f)
        runCatching {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${response.message}")
            val body = response.body ?: error("Empty response body")
            val total = body.contentLength()
            Log.i("DeepDive", "Summarizer model download ($label): content-length=${total / 1_048_576} MB")
            val tmp = File("${dest.absolutePath}.tmp")
            var downloaded = 0L
            var lastLoggedPct = -1
            tmp.outputStream().use { out ->
                body.byteStream().use { src ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (src.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            _state.value = ModelDownloadState.Downloading(downloaded.toFloat() / total)
                            if (pct / 10 > lastLoggedPct / 10) {
                                lastLoggedPct = pct
                                Log.i("DeepDive", "Summarizer model download ($label): $pct% (${downloaded / 1_048_576}/${total / 1_048_576} MB)")
                            }
                        }
                    }
                }
            }
            tmp.renameTo(dest)
            Log.i("DeepDive", "Summarizer model download COMPLETE ($label): ${dest.length() / 1_048_576} MB on disk")
            _state.value = ModelDownloadState.Complete
            if (pendingUrl.isNotEmpty()) NotificationHelper.postReady(context, pendingUrl)
        }.onFailure { e ->
            Log.e("DeepDive", "Summarizer model download FAILED ($label)", e)
            File("${dest.absolutePath}.tmp").delete()
            _state.value = ModelDownloadState.Failed(e.message ?: "Download failed")
        }.isSuccess
    }
}
