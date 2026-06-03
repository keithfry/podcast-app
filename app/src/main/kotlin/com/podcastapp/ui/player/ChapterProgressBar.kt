package com.podcastapp.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.podcastapp.domain.model.Chapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun snapToChapter(
    rawMs: Long,
    chapters: List<Chapter>,
    thresholdMs: Long = 10_000L
): Long {
    val nearest = chapters.minByOrNull { abs(it.startTimeMs - rawMs) } ?: return rawMs
    return if (abs(nearest.startTimeMs - rawMs) <= thresholdMs) nearest.startTimeMs else rawMs
}

private enum class DragMode { NONE, FREE, SNAP }

@Composable
fun ChapterProgressBar(
    positionMs: Long,
    durationMs: Long,
    chapters: List<Chapter>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val markerActiveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val snapColor = MaterialTheme.colorScheme.tertiary

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var dragFraction by remember { mutableStateOf(0f) }

    val displayFraction = when {
        dragMode != DragMode.NONE -> dragFraction
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    val thumbRadius = if (dragMode == DragMode.SNAP) 12.dp else 8.dp
    val thumbColor = if (dragMode == DragMode.SNAP) snapColor else progressColor

    Canvas(
        modifier = modifier
            .height(40.dp)
            .pointerInput(durationMs, chapters) {
                var snapTimerJob: Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (durationMs <= 0) return@awaitEachGesture

                    dragMode = DragMode.NONE
                    dragFraction = (down.position.x / size.width).coerceIn(0f, 1f)

                    snapTimerJob = coroutineScope.launch {
                        delay(500L)
                        dragMode = DragMode.SNAP
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    var moved = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)

                        if (!moved && newFraction != dragFraction) {
                            moved = true
                            snapTimerJob?.cancel()
                            if (dragMode == DragMode.NONE) dragMode = DragMode.FREE
                        }

                        dragFraction = if (dragMode == DragMode.SNAP) {
                            val rawMs = (newFraction * durationMs).toLong()
                            val snappedMs = snapToChapter(rawMs, chapters)
                            (snappedMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        } else {
                            newFraction
                        }

                        change.consume()
                    } while (event.changes.any { it.pressed })

                    snapTimerJob?.cancel()

                    val seekMs = (dragFraction * durationMs).toLong()
                    dragMode = DragMode.NONE
                    onSeek(seekMs)
                }
            }
    ) {
        val trackH = 6.dp.toPx()
        val markerW = 2.dp.toPx()
        val markerH = 16.dp.toPx()
        val cy = size.height / 2f
        val trackTop = cy - trackH / 2f
        val thumbR = thumbRadius.toPx()

        // Background track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f)
        )

        // Progress fill
        drawRoundRect(
            color = progressColor,
            topLeft = Offset(0f, trackTop),
            size = Size((size.width * displayFraction).coerceAtLeast(0f), trackH),
            cornerRadius = CornerRadius(trackH / 2f)
        )

        // Chapter markers — skip index 0 (position 0 = track start)
        if (durationMs > 0) {
            chapters.drop(1).forEach { chapter ->
                val x = (chapter.startTimeMs.toFloat() / durationMs) * size.width
                val behind = chapter.startTimeMs <= (displayFraction * durationMs).toLong()
                drawRect(
                    color = if (behind) markerActiveColor else markerColor,
                    topLeft = Offset(x - markerW / 2f, cy - markerH / 2f),
                    size = Size(markerW, markerH)
                )
            }
        }

        // Seek thumb — only shown while dragging
        if (dragMode != DragMode.NONE) {
            drawCircle(
                color = thumbColor,
                radius = thumbR,
                center = Offset(size.width * dragFraction, cy)
            )
        }
    }
}
