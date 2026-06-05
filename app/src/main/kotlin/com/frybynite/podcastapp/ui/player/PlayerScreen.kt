package com.frybynite.podcastapp.ui.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.res.Configuration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    audioUrl: String,
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val currentIdx by vm.currentChapterIndex.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val playbackSpeed by vm.playbackSpeed.collectAsStateWithLifecycle()
    val podcastImageUrl by vm.podcastImageUrl.collectAsStateWithLifecycle()
    val podcastTitle by vm.podcastTitle.collectAsStateWithLifecycle()
    val episodeTitle by vm.episodeTitle.collectAsStateWithLifecycle()
    val sleepTimerSeconds by vm.sleepTimerSeconds.collectAsStateWithLifecycle()
    val currentPositionMs by vm.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by vm.durationMs.collectAsStateWithLifecycle()
    val deepDiveState by vm.deepDiveState.collectAsStateWithLifecycle()
    val deepDiveChapterIndex by vm.deepDiveChapterIndex.collectAsStateWithLifecycle()
    val cachedDeepDiveUrls by vm.cachedDeepDiveUrls.collectAsStateWithLifecycle()
    val modelDownloadState by vm.modelDownloadState.collectAsStateWithLifecycle()
    var showSleepSheet by remember { mutableStateOf(false) }
    val currentChapter = chapters.getOrNull(currentIdx)
    var showSpeedSheet by remember { mutableStateOf(false) }
    var draggingPositionMs by remember { mutableStateOf<Long?>(null) }
    var snapHoverIdx by remember { mutableStateOf<Int?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val hasNotificationPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
    var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationPermissionGranted = granted }

    LaunchedEffect(audioUrl) {
        vm.connect(audioUrl)
    }

    if (showSleepSheet) {
        SleepTimerBottomSheet(
            activeSeconds = sleepTimerSeconds,
            onSelect = { minutes -> vm.setSleepTimer(minutes); showSleepSheet = false },
            onDismiss = { showSleepSheet = false }
        )
    }

    if (showSpeedSheet) {
        SpeedBottomSheet(
            currentSpeed = playbackSpeed,
            onSpeedChange = { vm.setSpeed(it) },
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (deepDiveState is DeepDiveState.ModelRequired) {
        AlertDialog(
            onDismissRequest = { vm.dismissDeepDiveError() },
            title = { Text("Download AI Model") },
            text = { Text("\"More about this\" requires a ~1.3 GB on-device AI model. Download over Wi-Fi?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.downloadModel()
                    vm.dismissDeepDiveError()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissDeepDiveError() }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            vm.updateCurrentChapterIndex()
            delay(1_000L)
        }
    }

    LaunchedEffect(modelDownloadState) {
        if (modelDownloadState is com.frybynite.podcastapp.deepdive.ModelDownloadState.Complete
            && !notificationPermissionGranted) {
            snackbarHostState.showSnackbar("AI model ready — try \"More about this\" again")
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
        val best = matches?.firstOrNull() ?: return@rememberLauncherForActivityResult
        when (VoiceCommandHandler.parse(best)) {
            VoiceCommand.NEXT_CHAPTER -> vm.nextChapter()
            VoiceCommand.PREV_CHAPTER -> vm.prevChapter()
            VoiceCommand.SEEK_FORWARD -> vm.seekForward30s()
            VoiceCommand.SEEK_BACK -> vm.seekBack10s()
            VoiceCommand.OPEN_LINK -> currentChapter?.url?.let {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it))
            }
            VoiceCommand.SHARE_LINK -> currentChapter?.url?.let { url ->
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }, "Share link"
                ))
            }
            VoiceCommand.MORE_ABOUT_THIS -> vm.moreAboutThis()
            null -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(episodeTitle ?: podcastTitle ?: "Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSleepSheet = true }) {
                        if (sleepTimerSeconds != null) {
                            Text(
                                formatSleepTimer(sleepTimerSeconds!!),
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Icon(Icons.Filled.Bedtime, "Sleep timer")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                voiceLauncher.launch(
                    Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                    }
                )
            }) {
                Icon(Icons.Filled.Mic, "Voice command")
            }
        }
    ) { padding ->
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val chapterListState = rememberLazyListState()

        LaunchedEffect(currentIdx) {
            if (chapters.isNotEmpty() && snapHoverIdx == null)
                chapterListState.animateScrollToItem(currentIdx)
        }
        LaunchedEffect(snapHoverIdx) {
            val idx = snapHoverIdx ?: return@LaunchedEffect
            if (chapters.isNotEmpty()) chapterListState.animateScrollToItem(idx)
        }

        // Reusable content blocks
        val artworkAndControls: @Composable ColumnScope.(artworkSize: Int) -> Unit = { artworkSize ->
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(artworkSize.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = podcastImageUrl,
                    contentDescription = "Podcast artwork",
                    contentScale = ContentScale.Crop,
                    placeholder = androidx.compose.ui.res.painterResource(com.frybynite.podcastapp.R.drawable.ic_podcast_placeholder),
                    error = androidx.compose.ui.res.painterResource(com.frybynite.podcastapp.R.drawable.ic_podcast_placeholder),
                    fallback = androidx.compose.ui.res.painterResource(com.frybynite.podcastapp.R.drawable.ic_podcast_placeholder),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMs(draggingPositionMs ?: currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(48.dp)
                )
                ChapterProgressBar(
                    positionMs = currentPositionMs,
                    durationMs = durationMs,
                    chapters = if (deepDiveState is DeepDiveState.Playing) emptyList() else chapters,
                    onSeek = { ms -> vm.controller?.seekTo(ms) },
                    onSnapHoverIndex = { snapHoverIdx = it },
                    onDragging = { draggingPositionMs = it },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                IconButton(onClick = {
                    if (deepDiveState is DeepDiveState.Playing) vm.controller?.seekTo(0)
                    else vm.prevChapter()
                }) {
                    Icon(Icons.Filled.SkipPrevious, "Previous chapter", Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.seekBack10s() }) {
                    SeekIcon(seconds = 10, isForward = false)
                }
                IconButton(
                    onClick = {
                        val c = vm.controller
                        if (c?.isPlaying == true) c.pause() else c?.play()
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayCircle,
                        if (isPlaying) "Pause" else "Play",
                        Modifier.size(56.dp)
                    )
                }
                IconButton(onClick = { vm.seekForward30s() }) {
                    SeekIcon(seconds = 30, isForward = true)
                }
                IconButton(onClick = {
                    if (deepDiveState is DeepDiveState.Playing) vm.skipDeepDive()
                    else vm.nextChapter()
                }) {
                    Icon(Icons.Filled.SkipNext, "Next chapter", Modifier.size(40.dp))
                }
                TextButton(onClick = { showSpeedSheet = true }) {
                    Text(
                        "${"%.1f".format(playbackSpeed)}×",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            currentChapter?.url?.let { url ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                    }) { Text("Open") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }, "Share link"
                        ))
                    }) { Text("Share") }
                }
            }
        }

        val chapterList: @Composable ColumnScope.() -> Unit = {
            HorizontalDivider()
            Text(
                "Chapters",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(8.dp).align(Alignment.Start)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), state = chapterListState) {
                itemsIndexed(chapters) { idx, chapter ->
                    val isSnapHovered = snapHoverIdx == idx
                    val isActive = idx == currentIdx
                    val hasUrl = chapter.url != null
                    val revealScope = rememberCoroutineScope()
                    val revealWidth = 192.dp
                    val revealWidthPx = with(LocalDensity.current) { revealWidth.toPx() }
                    val offsetX = remember(chapter.episodeAudioUrl, chapter.startTimeMs) { Animatable(0f) }

                    fun snapTo(target: Float) = revealScope.launch {
                        offsetX.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
                    }
                    fun collapse() = snapTo(0f)

                    Box {
                        // Reveal panel (behind the row)
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(revealWidth)
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val enabledAlpha = if (hasUrl) 1f else 0.35f
                            // Open
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        if (hasUrl) {
                                            chapter.url?.let { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it)) }
                                        }
                                        collapse()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = "Open", modifier = Modifier.size(22.dp).graphicsLayer(alpha = enabledAlpha))
                                Text("Open", style = MaterialTheme.typography.labelSmall, modifier = Modifier.graphicsLayer(alpha = enabledAlpha))
                            }
                            // Share
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        if (hasUrl) {
                                            chapter.url?.let { url ->
                                                context.startActivity(Intent.createChooser(
                                                    Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, url)
                                                    }, "Share link"
                                                ))
                                            }
                                        }
                                        collapse()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(22.dp).graphicsLayer(alpha = enabledAlpha))
                                Text("Share", style = MaterialTheme.typography.labelSmall, modifier = Modifier.graphicsLayer(alpha = enabledAlpha))
                            }
                            // More
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        if (hasUrl) vm.moreAboutThis(chapter.url, idx)
                                        collapse()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.PlayCircle, contentDescription = "More", modifier = Modifier.size(22.dp).graphicsLayer(alpha = enabledAlpha))
                                Text("More", style = MaterialTheme.typography.labelSmall, modifier = Modifier.graphicsLayer(alpha = enabledAlpha))
                            }
                        }

                        // Chapter row (draggable, on top)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(offsetX.value.toInt(), 0) }
                                .background(
                                    when {
                                        isSnapHovered -> MaterialTheme.colorScheme.tertiaryContainer
                                        isActive -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                                .then(
                                    if (isSnapHovered) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.tertiary,
                                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                    ) else Modifier
                                )
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        revealScope.launch {
                                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealWidthPx, 0f))
                                        }
                                    },
                                    onDragStopped = { velocity ->
                                        val target = if (offsetX.value < -revealWidthPx * 0.35f || velocity < -600f) -revealWidthPx else 0f
                                        snapTo(target)
                                    }
                                )
                                .combinedClickable(
                                    onClick = { vm.jumpToChapter(chapter.startTimeMs); collapse() },
                                    onLongClick = { collapse() }
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatMs(chapter.startTimeMs),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(52.dp)
                            )
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (chapter.url != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    if (chapter.url in cachedDeepDiveUrls) {
                                        Icon(Icons.Filled.PlayCircle, contentDescription = "Deep dive ready", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    if (deepDiveState is DeepDiveState.Playing && deepDiveChapterIndex == idx) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(52.dp))
                            Text(
                                "More About This…",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { artworkAndControls(100) }
                VerticalDivider()
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { chapterList() }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                artworkAndControls(160)
                Spacer(Modifier.height(12.dp))
                chapterList()
            }
        }

        if (deepDiveState is DeepDiveState.Loading) {
            val step = (deepDiveState as DeepDiveState.Loading).step
            val stepLabel = when (step) {
                DeepDiveStep.FETCHING -> "Getting link details"
                DeepDiveStep.SUMMARIZING -> "Generating summary"
                DeepDiveStep.SYNTHESIZING -> "Converting to audio"
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "More About This",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Generating", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = stepLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (deepDiveState is DeepDiveState.Error) {
            LaunchedEffect(deepDiveState) {
                delay(3000)
                vm.dismissDeepDiveError()
            }
            Box(Modifier.fillMaxSize().padding(padding).padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = (deepDiveState as DeepDiveState.Error).message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (modelDownloadState is com.frybynite.podcastapp.deepdive.ModelDownloadState.Downloading) {
            val progress = (modelDownloadState as com.frybynite.podcastapp.deepdive.ModelDownloadState.Downloading).progress
            Box(Modifier.fillMaxSize().padding(padding).padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(0.8f)) {
                        Text("Downloading model: ${(progress * 100).toInt()}%")
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerBottomSheet(
    activeSeconds: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(15, 30, 45, 60)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Sleep Timer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            options.forEach { mins ->
                TextButton(
                    onClick = { onSelect(mins) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("$mins minutes")
                }
            }
            if (activeSeconds != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TextButton(
                    onClick = { onSelect(0) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel timer (${formatSleepTimer(activeSeconds)} left)")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedBottomSheet(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Playback Speed",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "${"%.1f".format(currentSpeed)}×",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..2.0f,
                steps = 14, // 16 positions - 2 endpoints - 1 = 14 internal steps → 0.1 increments
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.5×", style = MaterialTheme.typography.bodySmall)
                Text("2.0×", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


@Composable
private fun SeekIcon(seconds: Int, isForward: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Replay,
            contentDescription = if (isForward) "+${seconds}s" else "-${seconds}s",
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { scaleX = if (isForward) -1f else 1f }
        )
        Text(
            text = "$seconds",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private operator fun DeepDiveStep.compareTo(other: DeepDiveStep): Int = ordinal.compareTo(other.ordinal)

private fun formatSleepTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%d:%02d".format(m, s % 60)
}
