package com.frybynite.podcastapp.deepdive

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class TextSummarizerContractTest {
    private val summarizer: TextSummarizer = object : TextSummarizer {
        override fun isModelAvailable() = true
        override suspend fun summarize(text: String) = "Summary: ${text.take(10)}"
    }

    @Test fun `summarize returns non-empty string`() = runTest {
        val result = summarizer.summarize("A long article about technology.")
        assertTrue(result.isNotEmpty())
    }

    @Test fun `isModelAvailable returns boolean`() {
        assertTrue(summarizer.isModelAvailable())
    }
}
