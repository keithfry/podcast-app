package com.frybynite.podlore.service

import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Tests the URI resolution logic from PlaybackService.onAddMediaItems:
 *   if item has a localConfiguration.uri already → keep as-is
 *   else → set uri from mediaId (so ExoPlayer / CastPlayer can load the item)
 */
@RunWith(AndroidJUnit4::class)
class AddMediaItemsTest {

    private fun resolveUri(items: List<MediaItem>): List<MediaItem> =
        items.map { item ->
            if (item.localConfiguration?.uri != null) item
            else item.buildUpon().setUri(item.mediaId).build()
        }

    @Test fun itemWithUriReturnedUnchanged() {
        val item = MediaItem.Builder()
            .setMediaId("https://ep.mp3")
            .setUri("https://ep.mp3")
            .build()

        val result = resolveUri(listOf(item))

        assertEquals("https://ep.mp3", result[0].localConfiguration?.uri.toString())
        assertEquals("https://ep.mp3", result[0].mediaId)
    }

    @Test fun itemWithoutUriGetsUriFromMediaId() {
        val item = MediaItem.Builder()
            .setMediaId("https://ep.mp3")
            .build()

        val result = resolveUri(listOf(item))

        assertNotNull(result[0].localConfiguration?.uri)
        assertEquals("https://ep.mp3", result[0].localConfiguration?.uri.toString())
        assertEquals("https://ep.mp3", result[0].mediaId)
    }

    @Test fun mixedListResolvesCorrectly() {
        val withUri = MediaItem.Builder().setMediaId("https://a.mp3").setUri("https://a.mp3").build()
        val withoutUri = MediaItem.Builder().setMediaId("https://b.mp3").build()

        val result = resolveUri(listOf(withUri, withoutUri))

        assertEquals("https://a.mp3", result[0].localConfiguration?.uri.toString())
        assertEquals("https://b.mp3", result[1].localConfiguration?.uri.toString())
        assertEquals("https://b.mp3", result[1].mediaId)
    }

    @Test fun localFileUriPreservedWhenAlreadySet() {
        val item = MediaItem.Builder()
            .setMediaId("https://ep.mp3")
            .setUri("file:///data/user/0/com.example/files/ep.mp3")
            .build()

        val result = resolveUri(listOf(item))

        assertEquals(
            "file:///data/user/0/com.example/files/ep.mp3",
            result[0].localConfiguration?.uri.toString()
        )
    }

    @Test fun emptyListReturnsEmptyList() {
        assertEquals(emptyList<MediaItem>(), resolveUri(emptyList()))
    }
}
