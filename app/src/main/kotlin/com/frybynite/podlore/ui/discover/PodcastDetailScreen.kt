package com.frybynite.podlore.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.text.Html
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.frybynite.podlore.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    vm: DiscoverViewModel,
    onBack: () -> Unit,
    onSubscribeSuccess: () -> Unit,
) {
    val result by vm.selectedResult.collectAsStateWithLifecycle()
    val subscribeState by vm.subscribeState.collectAsStateWithLifecycle()
    val detailDescription by vm.detailDescription.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(subscribeState) {
        if (subscribeState is SubscribeState.Success) onSubscribeSuccess()
        if (subscribeState is SubscribeState.Error) {
            snackbarHostState.showSnackbar((subscribeState as SubscribeState.Error).message)
        }
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val podcast = result ?: return@Scaffold
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = podcast.artworkUrl,
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
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = podcast.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!detailDescription.isNullOrBlank()) {
                val plainText = remember(detailDescription) {
                    Html.fromHtml(detailDescription!!, Html.FROM_HTML_MODE_COMPACT).toString().trim()
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
                            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append("more…") }
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
                            if (!expanded && words.size > 30)
                                Modifier.clickable { expanded = true }
                            else Modifier
                        ),
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                when (subscribeState) {
                    is SubscribeState.Loading -> CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    else -> {
                        if (podcast.isSubscribed || subscribeState is SubscribeState.Success) {
                            OutlinedButton(onClick = {}, enabled = false) { Text("Subscribed") }
                        } else {
                            Button(onClick = { vm.subscribe(podcast.feedUrl) }) { Text("Subscribe") }
                        }
                    }
                }
            }
        }
    }
}
