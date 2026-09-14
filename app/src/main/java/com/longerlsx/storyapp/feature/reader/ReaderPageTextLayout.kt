package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ensureActive

object ReaderPageTextLayout {
    fun measureLines(
        content: String,
        availableWidthPx: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        paragraphSpacingPx: Float,
        cancellationContext: CoroutineContext = EmptyCoroutineContext,
    ): List<ReaderPageLine> {
        cancellationContext.ensureActive()
        if (content.isBlank() || availableWidthPx <= 0) {
            return emptyList()
        }

        val paragraphs = ReaderParagraphModel.toDisplayParagraphs(content)
        if (paragraphs.isEmpty()) {
            return emptyList()
        }

        val lines = mutableListOf<ReaderPageLine>()
        var paragraphTopPx = 0f
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            cancellationContext.ensureActive()
            val layoutResult = textMeasurer.measure(
                text = AnnotatedString(paragraph.text),
                style = textStyle,
                overflow = TextOverflow.Clip,
                softWrap = true,
                constraints = Constraints(
                    maxWidth = availableWidthPx.coerceAtLeast(1),
                ),
            )
            cancellationContext.ensureActive()
            repeat(layoutResult.lineCount) { lineIndex ->
                lines += ReaderPageLine(
                    startCharOffset = paragraph.startCharOffset + layoutResult.getLineStart(lineIndex),
                    endCharOffset = paragraph.startCharOffset + layoutResult.getLineEnd(lineIndex, visibleEnd = true),
                    topPx = paragraphTopPx + layoutResult.getLineTop(lineIndex),
                    bottomPx = paragraphTopPx + layoutResult.getLineBottom(lineIndex),
                )
            }
            paragraphTopPx += layoutResult.size.height.toFloat()
            if (paragraphIndex < paragraphs.lastIndex) {
                paragraphTopPx += paragraphSpacingPx
            }
        }
        cancellationContext.ensureActive()
        return lines
    }
}
