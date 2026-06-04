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

    private fun isOpenClSupported(): Boolean = try {
        System.loadLibrary("OpenCL"); true
    } catch (e: UnsatisfiedLinkError) { false }

    private fun ensureLoaded() {
        if (inference != null) return
        val (modelFile, backend, label) = if (isOpenClSupported() && gpuModelFile.isValid()) {
            Triple(gpuModelFile, LlmInference.Backend.GPU, "GPU")
        } else if (cpuModelFile.isValid()) {
            Triple(cpuModelFile, LlmInference.Backend.CPU, "CPU")
        } else {
            error("No valid model available")
        }
        Log.i("DeepDive", "Loading $label model: ${modelFile.name}")
        inference = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setPreferredBackend(backend)
                .build()
        )
        Log.i("DeepDive", "$label model loaded successfully")
    }

    override suspend fun summarize(text: String): String = withContext(Dispatchers.Default) {
        ensureLoaded()
        val prompt = """Summarize this article in 3-4 sentences for a podcast listener:

$text

Summary:"""
        inference!!.generateResponse(prompt).trim()
    }
}
