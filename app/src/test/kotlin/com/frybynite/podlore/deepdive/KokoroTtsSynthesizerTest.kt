package com.frybynite.podlore.deepdive

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        context = mockk()
        client = mockk()
        every { context.cacheDir } returns tempFolder.root
        synthesizer = KokoroTtsSynthesizer(context, client, endpointUrl = "https://test.modal.run/tts")
    }

    private fun mockCall(response: Response): Call {
        val call = mockk<Call>()
        every { call.execute() } returns response
        every { client.newCall(any()) } returns call
        return call
    }

    private fun makeResponse(code: Int, bodyBytes: ByteArray? = null): Response {
        val response = mockk<Response>()
        every { response.code } returns code
        every { response.isSuccessful } returns (code in 200..299)
        every { response.message } returns ""
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

    @Test(expected = IllegalStateException::class)
    fun `503 response throws exception`() = runTest {
        val response = makeResponse(503)
        mockCall(response)

        synthesizer.synthesizeToFile("Error test")
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
