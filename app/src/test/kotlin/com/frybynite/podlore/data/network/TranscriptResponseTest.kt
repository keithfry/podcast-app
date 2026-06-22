package com.frybynite.podlore.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test
import kotlin.test.assertEquals

class TranscriptResponseTest {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(TranscriptResponse::class.java)

    private val json = """
        {
          "version": "1.0.0",
          "segments": [
            {"startTime": 0.0, "endTime": 3.2, "text": "Welcome to AI Daily Radar."},
            {"startTime": 3.2, "endTime": 7.8, "text": "Today we have twelve stories."}
          ]
        }
    """.trimIndent()

    @Test fun `parses two segments`() {
        val response = adapter.fromJson(json)!!
        assertEquals(2, response.segments.size)
    }

    @Test fun `parses segment fields`() {
        val seg = adapter.fromJson(json)!!.segments[1]
        assertEquals(3.2f, seg.startTime)
        assertEquals(7.8f, seg.endTime)
        assertEquals("Today we have twelve stories.", seg.text)
    }

    @Test fun `toSegments maps to domain model`() {
        val segments = adapter.fromJson(json)!!.toSegments()
        assertEquals(2, segments.size)
        assertEquals(0.0f, segments[0].startTimeSec)
        assertEquals(3.2f, segments[0].endTimeSec)
        assertEquals("Welcome to AI Daily Radar.", segments[0].text)
    }

    @Test fun `toSegments preserves order`() {
        val segments = adapter.fromJson(json)!!.toSegments()
        assertEquals(3.2f, segments[1].startTimeSec)
    }

    @Test fun `empty segments list`() {
        val response = adapter.fromJson("""{"version":"1.0.0","segments":[]}""")!!
        assertEquals(emptyList(), response.toSegments())
    }
}
