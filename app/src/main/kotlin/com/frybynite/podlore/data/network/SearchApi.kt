package com.frybynite.podlore.data.network

import com.frybynite.podlore.domain.model.PodcastSearchResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest

class SearchApi(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val podcastIndexKey: String,
    private val podcastIndexSecret: String,
) {

    suspend fun searchItunes(query: String): List<PodcastSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?media=podcast&term=$encoded&limit=20"
        val json = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("iTunes HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty body")
        }
        val response = moshi.adapter(ItunesSearchResponse::class.java).fromJson(json)
            ?: return@withContext emptyList()
        response.results.mapNotNull { item ->
            val feedUrl = item.feedUrl ?: return@mapNotNull null
            PodcastSearchResult(
                feedUrl = feedUrl,
                title = item.collectionName ?: "",
                author = item.artistName ?: "",
                artworkUrl = item.artworkUrl600,
                description = item.description,
            )
        }
    }

    suspend fun searchPodcastIndex(query: String): List<PodcastSearchResult> = withContext(Dispatchers.IO) {
        if (podcastIndexKey.isBlank()) return@withContext emptyList()
        val timestamp = System.currentTimeMillis() / 1000
        val auth = sha1Hex("$podcastIndexKey$podcastIndexSecret$timestamp")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.podcastindex.org/api/1.0/search/byterm?q=$encoded"
        val request = Request.Builder()
            .url(url)
            .addHeader("X-Auth-Key", podcastIndexKey)
            .addHeader("X-Auth-Date", timestamp.toString())
            .addHeader("Authorization", auth)
            .addHeader("User-Agent", "PodcastApp/1.0")
            .build()
        val json = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("PodcastIndex HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty body")
        }
        val response = moshi.adapter(PodcastIndexSearchResponse::class.java).fromJson(json)
            ?: return@withContext emptyList()
        response.feeds.mapNotNull { feed ->
            val feedUrl = feed.url ?: return@mapNotNull null
            PodcastSearchResult(
                feedUrl = feedUrl,
                title = feed.title ?: "",
                author = feed.author ?: "",
                artworkUrl = feed.artwork,
                description = feed.description,
            )
        }
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
