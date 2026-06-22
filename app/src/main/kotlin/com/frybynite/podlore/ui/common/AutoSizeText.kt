package com.frybynite.podlore.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * Text that shrinks its font size down to [minFontSize] until the content fits
 * in the available width on a single line.
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxFontSize: TextUnit = style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp,
    minFontSize: TextUnit = 10.sp,
) {
    var fontSize by remember(text, maxFontSize) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text, maxFontSize) { mutableStateOf(false) }

    Text(
        text = text,
        style = style.copy(fontSize = fontSize),
        maxLines = 2,
        softWrap = true,
        overflow = TextOverflow.Clip,
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > minFontSize) {
                val next = (fontSize.value - 1f).sp
                fontSize = if (next > minFontSize) next else minFontSize
            } else {
                readyToDraw = true
            }
        }
    )
}
