package com.frybynite.podcastapp.deepdive

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object DeepDiveRouter {
    private val _pendingUrl = MutableSharedFlow<String>(replay = 1)
    val pendingUrl: SharedFlow<String> = _pendingUrl

    fun emit(url: String) { _pendingUrl.tryEmit(url) }
}
