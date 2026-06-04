package com.frybynite.podcastapp.deepdive

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaTextSummarizer @Inject constructor(
    @ApplicationContext private val context: Context
) : TextSummarizer {

    private var inference: LlmInference? = null

    private val gpuModelFile get() = context.filesDir.resolve(ModelDownloadManager.GPU_MODEL_FILE)
    private val cpuModelFile get() = context.filesDir.resolve(ModelDownloadManager.CPU_MODEL_FILE)

    // Model is ~1.3 GB; anything under 100 MB is a corrupt/failed download
    private fun File.isValid() = exists() && length() > 100_000_000L

    override fun isModelAvailable(): Boolean = gpuModelFile.isValid() || cpuModelFile.isValid()

    private fun ensureLoaded() {
        if (inference != null) return
        // Try GPU model first, fall back to CPU
        if (gpuModelFile.isValid()) {
            runCatching {
                Log.i("DeepDive", "Loading GPU model")
                inference = LlmInference.createFromOptions(
                    context,
                    LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(gpuModelFile.absolutePath)
                        .setMaxTokens(512)
                        .setPreferredBackend(LlmInference.Backend.GPU)
                        .build()
                )
                Log.i("DeepDive", "GPU model loaded successfully")
            }.onFailure { e ->
                Log.w("DeepDive", "GPU model failed to load, falling back to CPU", e)
                inference = null
            }
        }
        if (inference == null && cpuModelFile.isValid()) {
            Log.i("DeepDive", "Loading CPU model")
            inference = LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(cpuModelFile.absolutePath)
                    .setMaxTokens(512)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
            )
            Log.i("DeepDive", "CPU model loaded successfully")
        }
        checkNotNull(inference) { "No valid model available" }
    }

    override suspend fun summarize(text: String): String = withContext(Dispatchers.Default) {
        ensureLoaded()
        val prompt = """Summarize this article in 3-4 sentences for a podcast listener:

$text

Summary:"""
        inference!!.generateResponse(prompt).trim()
    }
}
