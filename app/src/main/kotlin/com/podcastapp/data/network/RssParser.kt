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
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

    fun parse(xml: String): ParsedFeed {
        val parser: XmlPullParser = try {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            factory.newPullParser()
        } catch (_: Exception) {
            // Fallback for JVM unit tests where Android stubs return null
            val kxml = Class.forName("org.kxml2.io.KXmlParser").getDeclaredConstructor().newInstance() as XmlPullParser
            kxml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            kxml
        }
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
                    when (parser.name) {
                        "channel" -> inChannel = true
                        "item" -> {
                            inItem = true
                            epTitle = ""; epAudioUrl = ""; epPubDate = 0L
                            epDurationSeconds = 0; epChaptersUrl = null
                        }
                        "enclosure" -> if (inItem) epAudioUrl = parser.getAttributeValue(null, "url") ?: ""
                        "chapters" -> if (inItem) epChaptersUrl = parser.getAttributeValue(null, "url")
                    }
                }
                XmlPullParser.TEXT -> currentText = parser.text?.trim() ?: ""
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
                    inItem && parser.name == "title" -> epTitle = currentText
                    inItem && parser.name == "pubDate" -> epPubDate =
                        runCatching { dateFormat.parse(currentText)?.time ?: 0L }.getOrDefault(0L)
                    inItem && parser.name == "duration" -> epDurationSeconds = parseDuration(currentText)
                    inChannel && !inItem && parser.name == "title" -> podcastTitle = currentText
                    inChannel && !inItem && parser.name == "link" -> podcastLink = currentText
                    inChannel && !inItem && parser.name == "description" -> podcastDescription = currentText
                    inChannel && !inItem && parser.name == "author" -> podcastAuthor = currentText
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
