package com.podcastapp.service

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

class PlaybackService : MediaLibraryService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = null
}
