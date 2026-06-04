package com.frybynite.podcastapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.frybynite.podcastapp.deepdive.DeepDiveRouter
import com.frybynite.podcastapp.deepdive.NotificationHelper
import com.frybynite.podcastapp.ui.PodcastNavGraph
import com.frybynite.podcastapp.ui.theme.PodcastAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PodcastAppTheme {
                PodcastNavGraph()
            }
        }
        handleDeepDiveIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepDiveIntent(intent)
    }

    private fun handleDeepDiveIntent(intent: Intent?) {
        val url = intent?.getStringExtra(NotificationHelper.EXTRA_DEEP_DIVE_URL) ?: return
        if (url.isNotEmpty()) DeepDiveRouter.emit(url)
    }
}
