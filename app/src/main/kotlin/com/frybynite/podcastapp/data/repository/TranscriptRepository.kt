package com.frybynite.podcastapp.data.repository

import android.util.Log
import com.frybynite.podcastapp.data.network.FeedApi
import com.frybynite.podcastapp.data.network.TranscriptResponse
import com.frybynite.podcastapp.data.network.toSegments
import com.frybynite.podcastapp.domain.model.TranscriptSegment
import com.squareup.moshi.Moshi
import java.io.File

private const val TAG = "TranscriptRepo"

class TranscriptRepository(
    private val feedApi: FeedApi,
    private val moshi: Moshi,
    private val transcriptsDir: File
) {
    init { transcriptsDir.mkdirs() }

    private val adapter by lazy { moshi.adapter(TranscriptResponse::class.java) }

    suspend fun fetchTranscript(transcriptUrl: String): List<TranscriptSegment> {
        val cacheFile = File(transcriptsDir, "${Math.abs(transcriptUrl.hashCode())}.json")
        if (cacheFile.exists()) {
            Log.d(TAG, "fetchTranscript: cache hit for $transcriptUrl")
            return adapter.fromJson(cacheFile.readText())?.toSegments() ?: emptyList()
        }
        Log.i(TAG, "fetchTranscript: fetching $transcriptUrl")
        val response = feedApi.fetchTranscript(transcriptUrl)
        cacheFile.writeText(adapter.toJson(response))
        return response.toSegments()
    }
}
