package com.frybynite.podlore.deepdive

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCoreTextSummarizer @Inject constructor(
    @ApplicationContext private val context: Context
) : TextSummarizer {

    // null = unknown (status check in flight), true = may work, false = device unsupported
    @Volatile var supported: Boolean? = null
        private set

    private val model by lazy { Generation.getClient() }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val status = model.checkStatus()
                supported = status != FeatureStatus.UNAVAILABLE
                Log.i("DeepDive", "AiCore: status=$status supported=$supported")
                if (status == FeatureStatus.AVAILABLE) model.warmup()
            }.onFailure {
                supported = false
                Log.w("DeepDive", "AiCore: status check failed", it)
            }
        }
    }

    override fun isModelAvailable(): Boolean = supported != false

    override suspend fun summarize(text: String, existingSummary: String?): String = withContext(Dispatchers.IO) {
        val status = model.checkStatus()
        when (status) {
            FeatureStatus.UNAVAILABLE -> {
                supported = false
                error("Gemini Nano unavailable on this device")
            }
            FeatureStatus.DOWNLOADABLE -> {
                Log.i("DeepDive", "AiCore: triggering Gemini Nano system download")
                model.download().collect { ds ->
                    when (ds) {
                        is DownloadStatus.DownloadProgress ->
                            Log.i("DeepDive", "AiCore: download ${ds.totalBytesDownloaded} bytes")
                        is DownloadStatus.DownloadCompleted ->
                            Log.i("DeepDive", "AiCore: Gemini Nano download complete")
                        is DownloadStatus.DownloadFailed ->
                            error("Gemini Nano download failed: ${ds.e.message}")
                        else -> {}
                    }
                }
                supported = true
            }
            FeatureStatus.DOWNLOADING -> error("Gemini Nano is downloading — retry shortly")
            FeatureStatus.AVAILABLE -> supported = true
        }

        val prompt = buildPrompt(text, existingSummary)
        Log.i("DeepDive", "AiCore: generating, text=${text.length} chars existingSummary=${existingSummary != null}")
        val response = model.generateContent(prompt)
        val result = response.candidates
            .mapNotNull { it.text }
            .firstOrNull()
            ?.trim()
            ?: error("AiCore returned empty response")
        Log.i("DeepDive", "AiCore: on-device SUCCESS, output=${result.length} chars")
        result
    }

    private fun buildPrompt(text: String, existingSummary: String?): String {
        val sentenceTarget = when {
            text.length < 1_000 -> "2 to 3 sentences"
            text.length < 3_000 -> "4 to 5 sentences"
            text.length < 6_000 -> "6 to 8 sentences"
            else                -> "a paragraph per major theme"
        }
        val knownLine = if (existingSummary != null) "\nWhat they already know: $existingSummary\n" else ""
        val depthLine = if (existingSummary != null) "Go beyond what they already know. " else ""
        return """You are helping a podcast listener go deeper on a story.
$knownLine
Full article: $text

${depthLine}Focus on technical details, how it works, implications, and anything surprising or non-obvious. Write in clear, natural spoken prose — avoid bullet points, headers, markdown, abbreviations, and symbols. Aim for $sentenceTarget."""
    }
}
