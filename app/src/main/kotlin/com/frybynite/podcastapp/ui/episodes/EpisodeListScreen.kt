package com.frybynite.podcastapp.ui.episodes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import coil.compose.AsyncImage
import com.frybynite.podcastapp.R
import kotlinx.coroutines.launch
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
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val podcastImageUrl by vm.podcastImageUrl.collectAsStateWithLifecycle()
    val showHeard by vm.showHeard.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
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
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = showHeard,
                            onCheckedChange = { vm.toggleShowHeard() }
                        )
                        Text(
                            "Show heard",
                            modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    HorizontalDivider()
                }
                items(episodes, key = { it.audioUrl }) { episode ->
                    AnimatedVisibility(
                        visible = showHeard || !episode.isHeard,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            EpisodeRow(
                                episode = episode,
                                fallbackImageUrl = podcastImageUrl,
                                downloadProgress = downloadProgress[episode.audioUrl],
                                onClick = { onEpisodeClick(episode.audioUrl) },
                                onDownload = { vm.onPlayPause(episode) },
                                onToggleHeard = { vm.setEpisodeHeard(episode.audioUrl, !episode.isHeard) }
                            )
                            HorizontalDivider()
                        }
                    }
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
    downloadProgress: Float? = null,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onToggleHeard: () -> Unit = {}
) {
    val revealWidth = 80.dp
    val revealWidthPx = with(LocalDensity.current) { revealWidth.toPx() }
    val offsetX = remember(episode.audioUrl) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun snapTo(target: Float) = scope.launch {
        offsetX.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
    }

    val textColor = if (episode.isHeard)
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    else
        Color.Unspecified

    Box(modifier = Modifier.fillMaxWidth()) {
        // Reveal panel (always available — heard and unheard both support swipe)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealWidth)
                .matchParentSize()
                .background(if (episode.isHeard) Color(0xFF5B8DB8) else Color(0xFF5A9E6F))
                .clickable {
                    scope.launch { snapTo(0f) }
                    onToggleHeard()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (episode.isHeard) {
                Icon(Icons.Filled.Close, contentDescription = "Unheard", modifier = Modifier.size(22.dp), tint = Color.White)
                Text("Unheard", style = MaterialTheme.typography.labelSmall, color = Color.White)
            } else {
                Icon(Icons.Filled.DoneAll, contentDescription = "Heard", modifier = Modifier.size(22.dp), tint = Color.White)
                Text("Heard", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealWidthPx, 0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        val target = if (offsetX.value < -revealWidthPx * 0.35f || velocity < -600f)
                            -revealWidthPx else 0f
                        snapTo(target)
                    }
                )
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = episode.imageUrl ?: fallbackImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_podcast_placeholder),
                error = painterResource(R.drawable.ic_podcast_placeholder),
                fallback = painterResource(R.drawable.ic_podcast_placeholder),
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatDate(episode.pubDate)} · ${formatDuration(episode.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }
            if (!episode.isHeard) {
                when (episode.downloadStatus) {
                    DownloadStatus.NONE -> IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, "Download")
                    }
                    DownloadStatus.DONE -> Icon(
                        Icons.Filled.DownloadDone, "Downloaded",
                        modifier = Modifier.padding(12.dp)
                    )
                    else -> CircularProgressIndicator(
                        progress = { (downloadProgress ?: 0f).coerceAtLeast(0.05f) },
                        modifier = Modifier.size(48.dp).padding(end = 12.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
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
