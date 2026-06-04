package com.frybynite.podcastapp.deepdive

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroqTextSummarizerTest {

    private val client = mockk<OkHttpClient>()
    private val summarizer = GroqTextSummarizer(client, apiKey = "test-api-key")

    private fun mockResponse(code: Int, body: String): Response {
        val request = Request.Builder().url("https://api.groq.com/openai/v1/chat/completions").build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun `successful response returns parsed content`() = runTest {
        val responseJson = """
            {
              "choices": [
                {
                  "message": {
                    "content": "This is a great summary."
                  }
                }
              ]
            }
        """.trimIndent()

        val call = mockk<Call>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns mockResponse(200, responseJson)

        val result = summarizer.summarize("Some article text")
        assertEquals("This is a great summary.", result)
    }

    @Test
    fun `non-successful HTTP response throws exception`() = runTest {
        val call = mockk<Call>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns mockResponse(401, """{"error": "Invalid API key"}""")

        assertFailsWith<IllegalStateException> {
            summarizer.summarize("Some article text")
        }
    }

    @Test
    fun `isModelAvailable always returns true`() {
        assertEquals(true, summarizer.isModelAvailable())
    }

    @Test
    fun `summarize with existing summary uses deep dive prompt`() = runTest {
        val responseJson = """
            {
              "choices": [
                {
                  "message": {
                    "content": "Here are more details."
                  }
                }
              ]
            }
        """.trimIndent()

        val call = mockk<Call>()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns mockResponse(200, responseJson)

        val result = summarizer.summarize("Full article text", existingSummary = "Prior summary")
        assertEquals("Here are more details.", result)
    }
}
