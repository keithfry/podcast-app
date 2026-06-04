package com.frybynite.podcastapp.deepdive

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KokoroTtsSynthesizerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var client: OkHttpClient
    private lateinit var synthesizer: KokoroTtsSynthesizer

    @Before
    fun setUp() {
        context = mockk()
        client = mockk()
        every { context.cacheDir } returns tempFolder.root
        synthesizer = KokoroTtsSynthesizer(context, client, hfToken = "test-hf-token")
    }

    private fun mockCall(response: Response): Call {
        val call = mockk<Call>()
        every { call.execute() } returns response
        every { client.newCall(any()) } returns call
        return call
    }

    private fun mockCallSequence(first: Response, second: Response): Call {
        val call = mockk<Call>()
        every { client.newCall(any()) } returns call
        var callCount = 0
        every { call.execute() } answers {
            if (callCount++ == 0) first else second
        }
        return call
    }

    private fun makeResponse(code: Int, bodyBytes: ByteArray? = null): Response {
        val response = mockk<Response>()
        every { response.code } returns code
        every { response.isSuccessful } returns (code in 200..299)
        every { response.close() } returns Unit
        if (bodyBytes != null) {
            val buffer = Buffer().write(bodyBytes)
            val body = mockk<ResponseBody>()
            every { body.byteStream() } returns buffer.inputStream()
            every { response.body } returns body
        } else {
            every { response.body } returns null
        }
        return response
    }

    @Test
    fun `successful response writes bytes to file`() = runTest {
        val audioBytes = byteArrayOf(0x52, 0x49, 0x46, 0x46) // RIFF header bytes
        val response = makeResponse(200, audioBytes)
        mockCall(response)

        val file = synthesizer.synthesizeToFile("Hello world")

        assertTrue(file.exists())
        assertTrue(file.name.startsWith("kokoro_"))
        assertTrue(file.name.endsWith(".wav"))
        assertEquals(audioBytes.toList(), file.readBytes().toList())
    }

    @Test
    fun `503 triggers retry and second call succeeds`() = runTest {
        val audioBytes = byteArrayOf(0x01, 0x02, 0x03)
        val firstResponse = makeResponse(503)
        val secondResponse = makeResponse(200, audioBytes)
        mockCallSequence(firstResponse, secondResponse)

        val file = synthesizer.synthesizeToFile("Retry test")

        verify(exactly = 2) { client.newCall(any()) }
        assertTrue(file.exists())
        assertEquals(audioBytes.toList(), file.readBytes().toList())
    }

    @Test(expected = IllegalStateException::class)
    fun `non-successful response throws exception`() = runTest {
        val response = makeResponse(500)
        mockCall(response)

        synthesizer.synthesizeToFile("Error test")
    }

    @Test
    fun `release is a no-op`() {
        // Should not throw
        synthesizer.release()
    }
}
