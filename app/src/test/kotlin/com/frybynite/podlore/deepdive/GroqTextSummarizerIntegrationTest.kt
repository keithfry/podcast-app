package com.frybynite.podlore.deepdive

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GroqTextSummarizerIntegrationTest {

    private lateinit var summarizer: GroqTextSummarizer

    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        val props = Properties()
        File("../local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
        val apiKey = props.getProperty("GROQ_API_KEY", "")
        assumeTrue("GROQ_API_KEY not set in local.properties", apiKey.isNotBlank())
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        summarizer = GroqTextSummarizer(client, apiKey = apiKey)
    }

    @Test fun summarize_shortText_returnsNonEmptySummary() = runTest {
        val article = """
            Artificial intelligence researchers at a leading university have developed a new technique
            for training large language models that reduces compute requirements by 40 percent.
            The method, called gradient sparsification, selectively skips weight updates during
            backpropagation based on a learned importance score. Early experiments on a 7 billion
            parameter model show that perplexity on standard benchmarks remains within 2 percent
            of a fully trained baseline, while cutting GPU hours nearly in half.
        """.trimIndent()

        val result = summarizer.summarize(article)

        assertNotNull(result)
        assertTrue(result.isNotBlank(), "Summary should not be blank")
        assertTrue(result.length > 20, "Summary too short: $result")
    }

    @Test fun summarize_withExistingSummary_returnsDeepDiveContent() = runTest {
        val article = """
            Quantum computing startup Q-Leap announced a 1000-qubit processor that maintains
            coherence for 10 milliseconds at room temperature using a novel topological qubit design.
            The qubits are encoded in Majorana fermions, which are inherently protected from
            local noise sources. The company claims this reduces error rates by three orders of
            magnitude compared to superconducting qubits, potentially making quantum error correction
            practical without the massive qubit overhead current approaches require.
        """.trimIndent()
        val existingSummary = "Q-Leap built a 1000-qubit room-temperature quantum processor."

        val result = summarizer.summarize(article, existingSummary = existingSummary)

        assertNotNull(result)
        assertTrue(result.isNotBlank(), "Deep-dive summary should not be blank")
        assertTrue(result.length > 20, "Summary too short: $result")
    }
}
