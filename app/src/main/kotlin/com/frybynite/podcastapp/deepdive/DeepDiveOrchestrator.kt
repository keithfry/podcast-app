package com.frybynite.podcastapp.deepdive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class DeepDiveOrchestrator @Inject constructor(
    private val fetcher: UrlContentFetcher,
    private val summarizer: TextSummarizer,
    private val tts: TtsSynthesizer
) {
    suspend fun process(url: String): File = withContext(Dispatchers.IO) {
        val text = fetcher.fetch(url)
        val summary = summarizer.summarize(text)
        tts.synthesizeToFile(summary)
    }
}
