package com.podcastapp.ui.podcasts

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.podcastapp.domain.model.Podcast

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
            placeholder = androidx.compose.ui.res.painterResource(com.podcastapp.R.drawable.ic_podcast_placeholder),
            error = androidx.compose.ui.res.painterResource(com.podcastapp.R.drawable.ic_podcast_placeholder),
            fallback = androidx.compose.ui.res.painterResource(com.podcastapp.R.drawable.ic_podcast_placeholder),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(podcast.title, style = MaterialTheme.typography.titleMedium)
            Text(podcast.author, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddPodcastDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Podcast") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("RSS Feed URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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
