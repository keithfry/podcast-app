package com.frybynite.podcastapp.deepdive

import android.util.Log
import com.frybynite.podcastapp.ui.player.DeepDiveStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class DeepDiveOrchestrator @Inject constructor(
    private val fetcher: UrlContentFetcher,
    private val summarizer: TextSummarizer,
    private val tts: TtsSynthesizer,
    private val client: OkHttpClient
) {
    suspend fun process(
        chapterUrl: String,
        episodeAudioUrl: String? = null,
        onStep: (DeepDiveStep) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        Log.i("DeepDive", "process: start url=$chapterUrl")
        onStep(DeepDiveStep.FETCHING)
        val existingSummary = episodeAudioUrl?.let { fetchExistingSummary(it, chapterUrl) }
        Log.i("DeepDive", "process: existingSummary=${if (existingSummary != null) "${existingSummary.length} chars" else "none"}")
        val text = fetcher.fetch(chapterUrl)
        Log.i("DeepDive", "process: fetched ${text.length} chars")
        onStep(DeepDiveStep.SUMMARIZING)
        val summary = summarizer.summarize(text, existingSummary)
        Log.i("DeepDive", "process: summary=${summary.length} chars — starting TTS")
        onStep(DeepDiveStep.SYNTHESIZING)
        val file = tts.synthesizeToFile(summary)
        Log.i("DeepDive", "process: TTS done file=${file.name} size=${file.length()} bytes")
        file
    }

    private fun fetchExistingSummary(episodeAudioUrl: String, chapterUrl: String): String? {
        val jsonUrl = episodeAudioUrl.replace(Regex("\\.(mp3|m4a|ogg)$"), ".json")
        return runCatching {
            val response = client.newCall(Request.Builder().url(jsonUrl).build()).execute()
            val body = response.use { it.body?.string() } ?: return@runCatching null
            val items = JSONObject(body).getJSONArray("items")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (item.optString("link") == chapterUrl) {
                    return@runCatching item.optString("summary").takeIf { it.isNotEmpty() }
                }
            }
            null
        }.onFailure { e ->
            Log.w("DeepDive", "Could not fetch episode JSON context", e)
        }.getOrNull()
    }
}
