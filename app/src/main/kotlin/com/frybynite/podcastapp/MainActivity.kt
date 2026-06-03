package com.frybynite.podcastapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    }
}
