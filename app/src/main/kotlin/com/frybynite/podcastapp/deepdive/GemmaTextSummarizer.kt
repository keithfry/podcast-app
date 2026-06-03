package com.frybynite.podcastapp.deepdive

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
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

    val modelFile get() = context.filesDir.resolve("models/gemma-2b-it-int4.bin")

    override fun isModelAvailable(): Boolean = modelFile.exists()

    private fun ensureLoaded() {
        if (inference != null) return
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .build()
        inference = LlmInference.createFromOptions(context, options)
    }

    override suspend fun summarize(text: String): String = withContext(Dispatchers.Default) {
        ensureLoaded()
        val prompt = """Summarize this article in 3-4 sentences for a podcast listener:

$text

Summary:"""
        inference!!.generateResponse(prompt).trim()
    }
}
