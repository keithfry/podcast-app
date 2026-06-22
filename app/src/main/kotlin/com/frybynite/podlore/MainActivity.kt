package com.frybynite.podlore

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.frybynite.podlore.deepdive.DeepDiveRouter
import com.frybynite.podlore.deepdive.NotificationHelper
import com.frybynite.podlore.ui.PodcastNavGraph
import com.frybynite.podlore.ui.theme.PodcastAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
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
