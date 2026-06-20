package com.frybynite.podcastapp.data.repository

import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.network.FeedApi
import com.frybynite.podcastapp.data.network.RssParser
import com.frybynite.podcastapp.data.network.SearchApi
import com.frybynite.podcastapp.domain.model.PodcastSearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SearchRepository(
    private val searchApi: SearchApi,
    private val podcastDao: PodcastDao,
    private val feedApi: FeedApi,
    private val rssParser: RssParser,
) {

    suspend fun search(query: String): List<PodcastSearchResult> = coroutineScope {
        val itunesResult = async { runCatching { searchApi.searchItunes(query) } }
        val piResult = async { runCatching { searchApi.searchPodcastIndex(query) } }

        val itunes = itunesResult.await()
        val pi = piResult.await()

        if (itunes.isFailure && pi.isFailure) {
            throw Exception("Both search APIs failed")
        }

        val merged = merge(
            itunes.getOrElse { emptyList() },
            pi.getOrElse { emptyList() }
        )
        merged.map { it.copy(isSubscribed = podcastDao.existsByUrl(it.feedUrl)) }
    }

    private fun merge(
        itunes: List<PodcastSearchResult>,
        podcastIndex: List<PodcastSearchResult>,
    ): List<PodcastSearchResult> {
        val seen = linkedMapOf<String, PodcastSearchResult>()
        for (result in itunes) seen[normalizeUrl(result.feedUrl)] = result
        for (result in podcastIndex) {
            val key = normalizeUrl(result.feedUrl)
            if (key !in seen) seen[key] = result
        }
        return seen.values.toList()
    }

    suspend fun fetchFeedDescription(feedUrl: String): String? = runCatching {
        rssParser.parseChannelDescription(feedApi.fetchXml(feedUrl))
    }.getOrNull()

    internal fun normalizeUrl(url: String): String =
        url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
}
