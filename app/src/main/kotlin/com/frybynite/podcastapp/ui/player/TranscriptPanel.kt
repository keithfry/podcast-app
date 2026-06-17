package com.frybynite.podcastapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.frybynite.podcastapp.domain.model.TranscriptSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptPanel(
    segments: List<TranscriptSegment>,
    activeIndex: Int,
    loading: Boolean,
    onSeek: (TranscriptSegment) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                itemsIndexed(segments) { idx, segment ->
                    val isActive = idx == activeIndex
                    Text(
                        text = segment.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeek(segment) }
                            .background(
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (idx < segments.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}
