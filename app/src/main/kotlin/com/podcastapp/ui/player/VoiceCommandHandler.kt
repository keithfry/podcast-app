package com.podcastapp.ui.player

enum class VoiceCommand { NEXT_CHAPTER, PREV_CHAPTER, SEEK_FORWARD, SEEK_BACK, OPEN_LINK, SHARE_LINK }

object VoiceCommandHandler {
    fun parse(input: String): VoiceCommand? = when (input.trim().lowercase()) {
        "next", "next section", "skip", "forward" -> VoiceCommand.NEXT_CHAPTER
        "back", "previous", "previous section", "go back" -> VoiceCommand.PREV_CHAPTER
        "fast forward", "skip forward" -> VoiceCommand.SEEK_FORWARD
        "rewind", "skip back" -> VoiceCommand.SEEK_BACK
        "open link", "open", "open article" -> VoiceCommand.OPEN_LINK
        "save link", "save", "add to list", "share link" -> VoiceCommand.SHARE_LINK
        else -> null
    }
}
