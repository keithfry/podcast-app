package com.frybynite.podcastapp.deepdive

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject

class UrlContentFetcher @Inject constructor(private val client: OkHttpClient) {

    fun fetch(url: String): String {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val html = response.use { it.body?.string() } ?: return ""
        val doc = Jsoup.parse(html)
        val text = (doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.body())
            ?.text() ?: ""
        return text.take(3000)
    }
}
