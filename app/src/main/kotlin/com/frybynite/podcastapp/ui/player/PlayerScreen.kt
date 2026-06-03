package com.frybynite.podcastapp.ui.player

import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.delay
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
    val sleepTimerSeconds by vm.sleepTimerSeconds.collectAsStateWithLifecycle()
    val currentPositionMs by vm.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by vm.durationMs.collectAsStateWithLifecycle()
    val deepDiveState by vm.deepDiveState.collectAsStateWithLifecycle()
    val modelDownloadState by vm.modelDownloadState.collectAsStateWithLifecycle()
    var showSleepSheet by remember { mutableStateOf(false) }
    val currentChapter = chapters.getOrNull(currentIdx)
    var showSpeedSheet by remember { mutableStateOf(false) }
    var draggingPositionMs by remember { mutableStateOf<Long?>(null) }
    var snapHoverIdx by remember { mutableStateOf<Int?>(null) }

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
                TextButton(onClick = { vm.downloadModel(); vm.dismissDeepDiveError() }) { Text("Download") }
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

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
        val best = matches?.firstOrNull() ?: return@rememberLauncherForActivityResult
        when (VoiceCommandHandler.parse(best)) {
            VoiceCommand.NEXT_CHAPTER -> vm.nextChapter()
            VoiceCommand.PREV_CHAPTER -> vm.prevChapter()
            VoiceCommand.SEEK_FORWARD -> vm.seekForward30s()
            VoiceCommand.SEEK_BACK -> vm.seekBack30s()
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
        topBar = {
            TopAppBar(
                title = { Text(podcastTitle ?: "Playing") },
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
                    chapters = chapters,
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
                IconButton(onClick = { vm.prevChapter() }) {
                    Icon(Icons.Filled.SkipPrevious, "Previous chapter", Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.seekBack30s() }) {
                    Icon(Icons.Filled.Replay, "-30s", Modifier.size(36.dp))
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
                    Icon(Icons.Filled.FastForward, "+30s", Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.nextChapter() }) {
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
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                .combinedClickable(
                                    onClick = { vm.controller?.seekTo(chapter.startTimeMs) },
                                    onLongClick = { showMenu = true }
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
                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open link") },
                                enabled = chapter.url != null,
                                onClick = {
                                    showMenu = false
                                    chapter.url?.let { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it)) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share link") },
                                enabled = chapter.url != null,
                                onClick = {
                                    showMenu = false
                                    chapter.url?.let { url ->
                                        context.startActivity(Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, url)
                                            }, "Share link"
                                        ))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("More about this") },
                                enabled = chapter.url != null,
                                onClick = {
                                    showMenu = false
                                    vm.moreAboutThis()
                                }
                            )
                        }
                    }
                    HorizontalDivider()
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Generating deep dive…")
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
