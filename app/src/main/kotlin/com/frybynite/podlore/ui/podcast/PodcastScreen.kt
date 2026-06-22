package com.frybynite.podlore.ui.podcast

import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import coil.compose.AsyncImage
import com.frybynite.podlore.R
import com.frybynite.podlore.ui.episodes.EpisodeRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    onUnsubscribed: (() -> Unit)? = null,
    vm: PodcastViewModel = hiltViewModel(),
) {
    val isSubscribed by vm.isSubscribed.collectAsStateWithLifecycle()
    val displayTitle by vm.displayTitle.collectAsStateWithLifecycle()
    val displayAuthor by vm.displayAuthor.collectAsStateWithLifecycle()
    val displayArtworkUrl by vm.displayArtworkUrl.collectAsStateWithLifecycle()
    val displayDescription by vm.displayDescription.collectAsStateWithLifecycle()
    val subscribeState by vm.subscribeState.collectAsStateWithLifecycle()
    val showUnsubscribeConfirm by vm.showUnsubscribeConfirm.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val showHeard by vm.showHeard.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val currentlyPlayingUrl by vm.currentlyPlayingUrl.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val pullState = rememberPullToRefreshState()
    val context = LocalContext.current
    val isAutomotive = context.packageManager.hasSystemFeature("android.hardware.type.automotive")

    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { vm.refresh() }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullState.endRefresh()
    }

    LaunchedEffect(subscribeState) {
        if (subscribeState is SubscribeUiState.Error) {
            snackbarHostState.showSnackbar((subscribeState as SubscribeUiState.Error).message)
        }
    }

    // Track subscription transitions: subscribed → unsubscribed triggers pop in Podcasts tab
    val prevSubscribed = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isSubscribed) {
        val prev = prevSubscribed.value
        prevSubscribed.value = isSubscribed
        if (prev == true && !isSubscribed) {
            onUnsubscribed?.invoke()
        }
    }

    if (showUnsubscribeConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissUnsubscribe,
            title = { Text("Remove Podcast?") },
            text = {
                Text(
                    "This will remove \"$displayTitle\" and delete all downloaded episodes and cached content from your device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = vm::confirmUnsubscribe,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissUnsubscribe) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isAutomotive) {
                        AndroidView(
                            factory = { ctx ->
                                MediaRouteButton(ctx).apply {
                                    routeSelector = MediaRouteSelector.Builder()
                                        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                                        .build()
                                }
                            },
                            modifier = Modifier.size(48.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .nestedScroll(pullState.nestedScrollConnection)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    PodcastHeader(
                        title = displayTitle,
                        author = displayAuthor,
                        artworkUrl = displayArtworkUrl,
                        description = displayDescription,
                        isSubscribed = isSubscribed,
                        subscribeState = subscribeState,
                        onSubscribe = vm::subscribe,
                        onUnsubscribe = vm::requestUnsubscribe,
                    )
                }

                if (isSubscribed) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = showHeard, onCheckedChange = { vm.toggleShowHeard() })
                            Text(
                                "Show heard",
                                modifier = Modifier.padding(start = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        HorizontalDivider()
                    }

                    items(episodes, key = { it.audioUrl }) { episode ->
                        AnimatedVisibility(
                            visible = showHeard || !episode.isHeard,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                EpisodeRow(
                                    episode = episode,
                                    fallbackImageUrl = displayArtworkUrl,
                                    downloadProgress = downloadProgress[episode.audioUrl],
                                    isCurrentlyPlaying = currentlyPlayingUrl == episode.audioUrl,
                                    isPlayingActive = isPlaying,
                                    onClick = { onEpisodeClick(episode.audioUrl) },
                                    onPlayPause = { vm.onPlayPause(episode) },
                                    onToggleHeard = { vm.setEpisodeHeard(episode.audioUrl, !episode.isHeard) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun PodcastHeader(
    title: String,
    author: String,
    artworkUrl: String?,
    description: String?,
    isSubscribed: Boolean,
    subscribeState: SubscribeUiState,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_podcast_placeholder),
                error = painterResource(R.drawable.ic_podcast_placeholder),
                fallback = painterResource(R.drawable.ic_podcast_placeholder),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (author.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!description.isNullOrBlank()) {
            val plainText = remember(description) {
                Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            }
            val words = remember(plainText) { plainText.split(Regex("\\s+")).filter { it.isNotEmpty() } }
            var expanded by remember { mutableStateOf(false) }
            val primaryColor = MaterialTheme.colorScheme.primary
            val displayText = remember(expanded, words, primaryColor) {
                if (expanded || words.size <= 30) {
                    buildAnnotatedString { append(plainText) }
                } else {
                    buildAnnotatedString {
                        append(words.take(30).joinToString(" "))
                        append(" ")
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append("more…")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!expanded && words.size > 30) Modifier.clickable { expanded = true }
                        else Modifier
                    ),
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                subscribeState is SubscribeUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                isSubscribed ->
                    OutlinedButton(
                        onClick = onUnsubscribe,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Unsubscribe") }
                else ->
                    Button(onClick = onSubscribe) { Text("Subscribe") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
