package com.podcastapp.ui.player

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceCommandHandlerTest {
    @Test fun `'next section' maps to NEXT_CHAPTER`() =
        assertEquals(VoiceCommand.NEXT_CHAPTER, VoiceCommandHandler.parse("next section"))

    @Test fun `'skip' maps to NEXT_CHAPTER`() =
        assertEquals(VoiceCommand.NEXT_CHAPTER, VoiceCommandHandler.parse("skip"))

    @Test fun `'next' maps to NEXT_CHAPTER`() =
        assertEquals(VoiceCommand.NEXT_CHAPTER, VoiceCommandHandler.parse("next"))

    @Test fun `'previous section' maps to PREV_CHAPTER`() =
        assertEquals(VoiceCommand.PREV_CHAPTER, VoiceCommandHandler.parse("previous section"))

    @Test fun `'back' maps to PREV_CHAPTER`() =
        assertEquals(VoiceCommand.PREV_CHAPTER, VoiceCommandHandler.parse("back"))

    @Test fun `'fast forward' maps to SEEK_FORWARD`() =
        assertEquals(VoiceCommand.SEEK_FORWARD, VoiceCommandHandler.parse("fast forward"))

    @Test fun `'skip forward' maps to SEEK_FORWARD`() =
        assertEquals(VoiceCommand.SEEK_FORWARD, VoiceCommandHandler.parse("skip forward"))

    @Test fun `'rewind' maps to SEEK_BACK`() =
        assertEquals(VoiceCommand.SEEK_BACK, VoiceCommandHandler.parse("rewind"))

    @Test fun `'open link' maps to OPEN_LINK`() =
        assertEquals(VoiceCommand.OPEN_LINK, VoiceCommandHandler.parse("open link"))

    @Test fun `'save link' maps to SHARE_LINK`() =
        assertEquals(VoiceCommand.SHARE_LINK, VoiceCommandHandler.parse("save link"))

    @Test fun `'add to list' maps to SHARE_LINK`() =
        assertEquals(VoiceCommand.SHARE_LINK, VoiceCommandHandler.parse("add to list"))

    @Test fun `unknown command returns null`() =
        assertNull(VoiceCommandHandler.parse("what is the weather"))

    @Test fun `empty string returns null`() =
        assertNull(VoiceCommandHandler.parse(""))

    @Test fun `case insensitive matching`() =
        assertEquals(VoiceCommand.NEXT_CHAPTER, VoiceCommandHandler.parse("NEXT SECTION"))
}
