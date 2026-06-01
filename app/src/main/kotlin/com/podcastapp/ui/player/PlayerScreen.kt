package com.podcastapp.ui.player

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
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
    val currentChapter = chapters.getOrNull(currentIdx)

    LaunchedEffect(audioUrl) {
        vm.connect()
        vm.loadAndPlay(audioUrl)
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
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentChapter?.title ?: "Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Playback controls
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
            }

            Spacer(Modifier.height(12.dp))

            // Chapter link actions
            currentChapter?.url?.let { url ->
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
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider()
            Text(
                "Chapters",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(8.dp).align(Alignment.Start)
            )

            // Chapter list
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(chapters) { idx, chapter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (idx == currentIdx) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { vm.controller?.seekTo(chapter.startTimeMs) }
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
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%d:%02d".format(m, s % 60)
}
