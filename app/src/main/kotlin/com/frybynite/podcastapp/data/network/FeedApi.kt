package com.frybynite.podcastapp.data.network

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class FeedApi(private val client: OkHttpClient, private val moshi: Moshi) {

    suspend fun fetchXml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("Empty body")
        }
    }

    suspend fun fetchChapters(url: String): ChaptersResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("Empty body")
        }
        moshi.adapter(ChaptersResponse::class.java).fromJson(json)
            ?: throw Exception("Failed to parse chapters JSON")
    }

    suspend fun fetchTranscript(url: String): TranscriptResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("Empty body")
        }
        moshi.adapter(TranscriptResponse::class.java).fromJson(json)
            ?: throw Exception("Failed to parse transcript JSON")
    }
}
