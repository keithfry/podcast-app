package com.frybynite.podcastapp.data.repository

import com.frybynite.podcastapp.data.db.dao.PodcastDao
import com.frybynite.podcastapp.data.network.SearchApi
import com.frybynite.podcastapp.domain.model.PodcastSearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SearchRepository(
    private val searchApi: SearchApi,
    private val podcastDao: PodcastDao,
) {

    suspend fun search(query: String): List<PodcastSearchResult> = coroutineScope {
        val itunesDeferred = async {
            runCatching { searchApi.searchItunes(query) }.getOrElse { emptyList() }
        }
        val piDeferred = async {
            runCatching { searchApi.searchPodcastIndex(query) }.getOrElse { emptyList() }
        }
        val merged = merge(itunesDeferred.await(), piDeferred.await())
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

    internal fun normalizeUrl(url: String): String =
        url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
}
