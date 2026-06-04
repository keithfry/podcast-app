package com.frybynite.podcastapp.data.storage

import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named

class CacheStorage @Inject constructor(
    @Named("podcasts_dir") private val root: File
) {
    fun podcastDir(feedUrl: String, podcastTitle: String): File =
        File(root, "${slug(podcastTitle)}-${hash8(feedUrl)}")

    fun episodeDir(feedUrl: String, podcastTitle: String, audioUrl: String, episodeTitle: String): File =
        File(podcastDir(feedUrl, podcastTitle), "${slug(episodeTitle)}-${hash8(audioUrl)}")

    fun mainAudioFile(feedUrl: String, podcastTitle: String, audioUrl: String, episodeTitle: String): File =
        File(episodeDir(feedUrl, podcastTitle, audioUrl, episodeTitle), "audio.${ext(audioUrl)}")

    fun metadataFile(feedUrl: String, podcastTitle: String, audioUrl: String, episodeTitle: String): File =
        File(episodeDir(feedUrl, podcastTitle, audioUrl, episodeTitle), "metadata.json")

    fun deepDiveFile(
        feedUrl: String, podcastTitle: String, audioUrl: String, episodeTitle: String,
        chapterUrl: String, chapterTitle: String?
    ): File = File(
        episodeDir(feedUrl, podcastTitle, audioUrl, episodeTitle),
        "more-${slug(chapterTitle ?: chapterUrl)}-${hash8(chapterUrl)}.wav"
    )

    private fun slug(text: String): String {
        val s = text.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')
        return s.ifEmpty { "untitled" }
    }

    private fun ext(url: String): String {
        val tail = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "")
        return tail.lowercase().takeIf { it.isNotEmpty() && it.length <= 4 } ?: "mp3"
    }

    private fun hash8(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }
}
