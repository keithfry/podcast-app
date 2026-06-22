package com.frybynite.podlore.deepdive

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
        if (gpuModelFile.isValid()) {
            runCatching {
                Log.i("DeepDive", "Loading GPU model: ${gpuModelFile.name}")
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
                Log.w("DeepDive", "GPU backend failed (${e.message?.take(120)}), trying CPU backend")
            }
        }
        if (inference == null && cpuModelFile.isValid()) {
            Log.i("DeepDive", "Loading CPU model: ${cpuModelFile.name}")
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
        if (inference == null) error("No valid model available")
    }

    override suspend fun summarize(text: String, existingSummary: String?): String = withContext(Dispatchers.Default) {
        ensureLoaded()
        val prompt = if (existingSummary != null) {
            """You are helping a podcast listener go deeper on a story they just heard.

What they already know: $existingSummary

Full article: $text

Go beyond the summary. Focus on technical details, how it works, implications, and anything surprising or non-obvious. Be concise — 4-5 sentences."""
        } else {
            """Summarize this article in 3-4 sentences for a podcast listener:

$text

Summary:"""
        }
        inference!!.generateResponse(prompt).trim()
    }
}
