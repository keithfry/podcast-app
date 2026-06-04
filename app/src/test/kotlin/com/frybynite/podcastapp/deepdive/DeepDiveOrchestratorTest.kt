package com.frybynite.podcastapp.deepdive

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class DeepDiveOrchestratorTest {
    private val fetcher = mockk<UrlContentFetcher>()
    private val summarizer = mockk<TextSummarizer>()
    private val tts = mockk<TtsSynthesizer>()
    private val client = mockk<OkHttpClient>(relaxed = true)
    private val orchestrator = DeepDiveOrchestrator(fetcher, summarizer, tts, client)

    @Test fun `process fetches, summarizes, then synthesizes`() = runTest {
        every { fetcher.fetch("https://example.com") } returns "Article text content"
        coEvery { summarizer.summarize("Article text content", any()) } returns "Short summary."
        val fakeFile = mockk<File>()
        coEvery { tts.synthesizeToFile("Short summary.") } returns fakeFile

        val result = orchestrator.process("https://example.com")

        assertEquals(fakeFile, result)
        coVerify(exactly = 1) { summarizer.summarize("Article text content", any()) }
        coVerify(exactly = 1) { tts.synthesizeToFile("Short summary.") }
    }
}
