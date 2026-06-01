package com.podcastapp.ui.episodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                EpisodeRow(episode = episode, onClick = { onEpisodeClick(episode.audioUrl) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(episode.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatDate(episode.pubDate)} · ${formatDuration(episode.durationSeconds)}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
private fun formatDate(millis: Long) = if (millis > 0) dateFmt.format(Date(millis)) else ""
private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
