package com.podcastapp.data.network

import com.podcastapp.domain.model.Episode
import com.podcastapp.domain.model.Podcast
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedFeed(val podcast: Podcast, val episodes: List<Episode>)

class RssParser {
    companion object {
        private const val NS_ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
        private const val NS_PODCAST = "https://podcastindex.org/namespace/1.0"
    }

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

    fun parse(xml: String): ParsedFeed {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var podcastTitle = ""
        var podcastLink = ""
        var podcastDescription = ""
        var podcastAuthor = ""
        var podcastImage: String? = null
        val episodes = mutableListOf<Episode>()

        var inChannel = false
        var inItem = false
        var epTitle = ""
        var epAudioUrl = ""
        var epPubDate = 0L
        var epDurationSeconds = 0
        var epChaptersUrl: String? = null
        var currentText = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentText = ""
                    when {
                        parser.name == "channel" -> inChannel = true
                        parser.name == "item" -> {
                            inItem = true
                            epTitle = ""; epAudioUrl = ""; epPubDate = 0L
                            epDurationSeconds = 0; epChaptersUrl = null
                        }
                        parser.name == "enclosure" -> if (inItem) epAudioUrl = parser.getAttributeValue(null, "url") ?: ""
                        parser.namespace == NS_PODCAST && parser.name == "chapters" -> if (inItem) epChaptersUrl = parser.getAttributeValue(null, "url")
                    }
                }
                XmlPullParser.TEXT -> currentText += (parser.text ?: "")
                XmlPullParser.END_TAG -> when {
                    parser.name == "item" -> {
                        episodes.add(
                            Episode(
                                audioUrl = epAudioUrl,
                                podcastFeedUrl = podcastLink,
                                title = epTitle,
                                pubDate = epPubDate,
                                durationSeconds = epDurationSeconds,
                                chaptersUrl = epChaptersUrl
                            )
                        )
                        inItem = false
                    }
                    inItem && parser.name == "title" -> epTitle = currentText.trim()
                    inItem && parser.name == "pubDate" -> epPubDate =
                        runCatching { dateFormat.parse(currentText.trim())?.time ?: 0L }.getOrDefault(0L)
                    inItem && parser.namespace == NS_ITUNES && parser.name == "duration" -> epDurationSeconds = parseDuration(currentText.trim())
                    inChannel && !inItem && parser.name == "title" -> podcastTitle = currentText.trim()
                    inChannel && !inItem && parser.name == "link" -> podcastLink = currentText.trim()
                    inChannel && !inItem && parser.name == "description" -> podcastDescription = currentText.trim()
                    inChannel && !inItem && parser.namespace == NS_ITUNES && parser.name == "author" -> podcastAuthor = currentText.trim()
                }
            }
            event = parser.next()
        }

        return ParsedFeed(
            podcast = Podcast(
                feedUrl = podcastLink,
                title = podcastTitle,
                author = podcastAuthor,
                description = podcastDescription,
                imageUrl = podcastImage
            ),
            episodes = episodes
        )
    }

    private fun parseDuration(s: String): Int {
        val parts = s.trim().split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> s.toIntOrNull() ?: 0
        }
    }
}
