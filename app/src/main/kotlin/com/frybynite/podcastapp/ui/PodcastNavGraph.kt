package com.frybynite.podcastapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.frybynite.podcastapp.ui.episodes.EpisodeListScreen
import com.frybynite.podcastapp.ui.player.PlayerScreen
import com.frybynite.podcastapp.ui.podcasts.PodcastListScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun PodcastNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "podcasts") {
        composable("podcasts") {
            PodcastListScreen(onPodcastClick = { feedUrl ->
                nav.navigate("episodes/${URLEncoder.encode(feedUrl, "UTF-8")}")
            })
        }
        composable(
            "episodes/{feedUrl}",
            arguments = listOf(navArgument("feedUrl") { type = NavType.StringType })
        ) {
            EpisodeListScreen(
                onBack = { nav.popBackStack() },
                onEpisodeClick = { audioUrl ->
                    nav.navigate("player/${URLEncoder.encode(audioUrl, "UTF-8")}")
                }
            )
        }
        composable(
            "player/{audioUrl}",
            arguments = listOf(navArgument("audioUrl") { type = NavType.StringType })
        ) { backStack ->
            val audioUrl = URLDecoder.decode(
                backStack.arguments?.getString("audioUrl") ?: "", "UTF-8"
            )
            PlayerScreen(audioUrl = audioUrl, onBack = { nav.popBackStack() })
        }
    }
}
