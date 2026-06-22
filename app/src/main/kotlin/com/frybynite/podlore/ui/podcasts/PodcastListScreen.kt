package com.frybynite.podlore.ui.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.frybynite.podlore.domain.model.Podcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastListScreen(
    onPodcastClick: (String) -> Unit,
    vm: PodcastListViewModel = hiltViewModel()
) {
    val podcasts by vm.podcasts.collectAsStateWithLifecycle(emptyList())
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Podcasts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add podcast")
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(podcasts, key = { it.feedUrl }) { podcast ->
                    PodcastRow(podcast = podcast, onClick = { onPodcastClick(podcast.feedUrl) })
                    HorizontalDivider()
                }
            }
        }

        error?.let { msg ->
            AlertDialog(
                onDismissRequest = { vm.dismissError() },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { vm.dismissError() }) { Text("OK") }
                }
            )
        }
    }

    if (showAddDialog) {
        AddPodcastDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url -> vm.addPodcast(url); showAddDialog = false }
        )
    }
}

@Composable
private fun PodcastRow(podcast: Podcast, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = podcast.imageUrl,
            contentDescription = null,
            placeholder = androidx.compose.ui.res.painterResource(com.frybynite.podlore.R.drawable.ic_podcast_placeholder),
            error = androidx.compose.ui.res.painterResource(com.frybynite.podlore.R.drawable.ic_podcast_placeholder),
            fallback = androidx.compose.ui.res.painterResource(com.frybynite.podlore.R.drawable.ic_podcast_placeholder),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(podcast.title, style = MaterialTheme.typography.titleMedium)
            Text(podcast.author, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val QUICK_FEEDS_ROW1 = listOf(
    "Raging Moderates" to "https://api.substack.com/feed/podcast/7157411/s/338591/private/3f666b6a-9d88-4054-82e4-1c167744b3aa.rss",
    "AI Daily" to "https://keithfry.github.io/web-pages/techradar/AI/podcast.rss",
)
private val QUICK_FEEDS_ROW2 = listOf(
    "Robotics Daily" to "https://keithfry.github.io/web-pages/techradar/Robotics/podcast.rss",
)

private val chipColors @Composable get() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0xFFD6EAF8),
    selectedContainerColor = Color(0xFF5DADE2),
)

@Composable
private fun AddPodcastDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Podcast") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_FEEDS_ROW1.forEach { (label, feedUrl) ->
                        FilterChip(
                            selected = url == feedUrl,
                            onClick = { url = feedUrl },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = chipColors,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_FEEDS_ROW2.forEach { (label, feedUrl) ->
                        FilterChip(
                            selected = url == feedUrl,
                            onClick = { url = feedUrl },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = chipColors,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("RSS Feed URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onAdd(url) },
                enabled = url.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
