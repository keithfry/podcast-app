package com.frybynite.podcastapp.deepdive

import android.util.Log
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
    suspend fun process(chapterUrl: String, episodeAudioUrl: String? = null): File = withContext(Dispatchers.IO) {
        val existingSummary = episodeAudioUrl?.let { fetchExistingSummary(it, chapterUrl) }
        val text = fetcher.fetch(chapterUrl)
        val summary = summarizer.summarize(text, existingSummary)
        tts.synthesizeToFile(summary)
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
