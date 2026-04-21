package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

object ReaderPageTextLayout {
    fun measureLines(
        content: String,
        availableWidthPx: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
    ): List<ReaderPageLine> {
        if (content.isBlank() || availableWidthPx <= 0) {
            return emptyList()
        }

        val layoutResult = textMeasurer.measure(
            text = AnnotatedString(content),
            style = textStyle,
            overflow = TextOverflow.Clip,
            softWrap = true,
            constraints = Constraints(
                maxWidth = availableWidthPx.coerceAtLeast(1),
            ),
        )

        return List(layoutResult.lineCount) { lineIndex ->
            ReaderPageLine(
                startCharOffset = layoutResult.getLineStart(lineIndex),
                endCharOffset = layoutResult.getLineEnd(lineIndex, visibleEnd = true),
                topPx = layoutResult.getLineTop(lineIndex),
                bottomPx = layoutResult.getLineBottom(lineIndex),
            )
        }
    }
}
