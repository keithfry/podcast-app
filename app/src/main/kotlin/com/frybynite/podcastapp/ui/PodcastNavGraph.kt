package com.frybynite.podcastapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.frybynite.podcastapp.ui.episodes.EpisodeListScreen
import com.frybynite.podcastapp.ui.player.MiniPlayerBar
import com.frybynite.podcastapp.ui.player.PlayerScreen
import com.frybynite.podcastapp.ui.podcasts.PodcastListScreen
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastNavGraph(
    vm: AppViewModel = hiltViewModel(),
) {
    val currentlyPlayingUrl by vm.currentlyPlayingUrl.collectAsStateWithLifecycle()
    val currentTitle by vm.currentTitle.collectAsStateWithLifecycle()
    val currentArtworkUrl by vm.currentArtworkUrl.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()

    val appState = remember { AppState(currentlyPlayingUrl = vm.currentlyPlayingUrl) }
    val showMiniPlayer by appState.showMiniPlayer.collectAsStateWithLifecycle()
    val isPlayerSheetOpen by appState.isPlayerSheetOpen.collectAsStateWithLifecycle()

    var playerAudioUrl by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(AppTab.Podcasts) }

    val libraryNav = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayerBar(
                        title = currentTitle,
                        artworkUrl = currentArtworkUrl,
                        isPlaying = isPlaying,
                        visible = showMiniPlayer,
                        onPlayPause = { if (isPlaying) vm.pause() else vm.resume() },
                        onExpand = {
                            playerAudioUrl = currentlyPlayingUrl
                            appState.openPlayer()
                        },
                    )
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == AppTab.Podcasts,
                            onClick = { selectedTab = AppTab.Podcasts },
                            icon = { Icon(Icons.Filled.Headphones, contentDescription = "Podcasts") },
                            label = { Text("Podcasts") },
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.Discover,
                            onClick = { selectedTab = AppTab.Discover },
                            icon = { Icon(Icons.Filled.Explore, contentDescription = "Discover") },
                            label = { Text("Discover") },
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (selectedTab) {
                    AppTab.Podcasts -> NavHost(navController = libraryNav, startDestination = "podcasts") {
                        composable("podcasts") {
                            PodcastListScreen(onPodcastClick = { feedUrl ->
                                libraryNav.navigate("episodes/${URLEncoder.encode(feedUrl, "UTF-8")}")
                            })
                        }
                        composable(
                            "episodes/{feedUrl}",
                            arguments = listOf(navArgument("feedUrl") { type = NavType.StringType }),
                        ) {
                            EpisodeListScreen(
                                onBack = { libraryNav.popBackStack() },
                                onEpisodeClick = { audioUrl ->
                                    playerAudioUrl = audioUrl
                                    appState.openPlayer()
                                },
                            )
                        }
                    }
                    AppTab.Discover -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center),
                    ) {
                        Text(
                            text = "Add Discover Feature Here",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        // Player overlay — lives in the same layout context so it always fills full screen
        // regardless of orientation. Slides up/down to open/close.
        val url = playerAudioUrl ?: currentlyPlayingUrl
        AnimatedVisibility(
            visible = isPlayerSheetOpen && url != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.fillMaxSize(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                if (url != null) {
                    PlayerScreen(
                        audioUrl = url,
                        onDismiss = { appState.closePlayer() },
                    )
                }
            }
        }
    }
}
