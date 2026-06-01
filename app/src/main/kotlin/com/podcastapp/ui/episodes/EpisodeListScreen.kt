package com.podcastapp.ui.episodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podcastapp.domain.model.DownloadStatus
import com.podcastapp.domain.model.Episode
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(episodes, key = { it.audioUrl }) { episode ->
                EpisodeRow(
                    episode = episode,
                    onClick = { onEpisodeClick(episode.audioUrl) },
                    onDownload = { vm.downloadEpisode(episode.audioUrl) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit, onDownload: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
