package com.frybynite.podcastapp.ui.episodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import coil.compose.AsyncImage
import com.frybynite.podcastapp.domain.model.DownloadStatus
import com.frybynite.podcastapp.domain.model.Episode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    vm: EpisodeListViewModel = hiltViewModel()
) {
    val episodes by vm.episodes.collectAsStateWithLifecycle(emptyList())
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val podcastImageUrl by vm.podcastImageUrl.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()
    val context = LocalContext.current
    val isAutomotive = context.packageManager.hasSystemFeature("android.hardware.type.automotive")

    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) {
            vm.refresh()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullState.endRefresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!isAutomotive) {
                        AndroidView(
                            factory = { ctx ->
                                MediaRouteButton(ctx).apply {
                                    val selector = MediaRouteSelector.Builder()
                                        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                                        .build()
                                    routeSelector = selector
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .nestedScroll(pullState.nestedScrollConnection)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(episodes, key = { it.audioUrl }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        fallbackImageUrl = podcastImageUrl,
                        onClick = { onEpisodeClick(episode.audioUrl) },
                        onDownload = { vm.downloadEpisode(episode.audioUrl) }
                    )
                    HorizontalDivider()
                }
            }
            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
internal fun EpisodeRow(
    episode: Episode,
    fallbackImageUrl: String? = null,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.imageUrl ?: fallbackImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = androidx.compose.ui.res.painterResource(com.frybynite.podcastapp.R.drawable.ic_podcast_placeholder),
            error = androidx.compose.ui.res.painterResource(com.frybynite.podcastapp.R.drawable.ic_podcast_placeholder),
            fallback = androidx.compose.ui.res.painterResource(com.frybynite.podcastapp.R.drawable.ic_podcast_placeholder),
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.small)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(episode.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatDate(episode.pubDate)} · ${formatDuration(episode.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        when (episode.downloadStatus) {
            DownloadStatus.NONE -> IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, "Download")
            }
            DownloadStatus.DONE -> Icon(
                Icons.Filled.DownloadDone, "Downloaded",
                modifier = Modifier.padding(12.dp)
            )
            else -> CircularProgressIndicator(Modifier.size(24.dp).padding(end = 12.dp))
        }
    }
}

private fun formatDate(millis: Long): String {
    if (millis <= 0) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
}
private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
