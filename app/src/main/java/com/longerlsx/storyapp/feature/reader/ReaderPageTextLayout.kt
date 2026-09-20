package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ReaderParagraphMeasurement(
    val startCharOffset: Int,
    val endCharOffset: Int,
    val layoutHeightPx: Int,
    val lastLineBottomPx: Float,
)

internal data class ReaderMeasuredChapterText(
    val lines: List<ReaderPageLine>,
    val paragraphsByStart: Map<Int, ReaderParagraphMeasurement>,
)

object ReaderPageTextLayout {
    fun measureLines(
        content: String,
        availableWidthPx: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        paragraphSpacingPx: Float,
        cancellationContext: CoroutineContext = EmptyCoroutineContext,
    ): List<ReaderPageLine> = measure(
        content, availableWidthPx, textMeasurer, textStyle, paragraphSpacingPx,
        cancellationContext, paragraphMeasurements = null,
    )

    internal fun measureChapter(
        content: String,
        availableWidthPx: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        paragraphSpacingPx: Float,
        cancellationContext: CoroutineContext = EmptyCoroutineContext,
    ): ReaderMeasuredChapterText {
        val paragraphs = mutableMapOf<Int, ReaderParagraphMeasurement>()
        val lines = measure(
            content, availableWidthPx, textMeasurer, textStyle, paragraphSpacingPx,
            cancellationContext, paragraphs,
        )
        return ReaderMeasuredChapterText(lines, paragraphs)
    }

    internal fun measureParagraph(
        paragraph: ReaderDisplayParagraph,
        availableWidthPx: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        cancellationContext: CoroutineContext,
    ): ReaderParagraphMeasurement = measurement(
        paragraph,
        measureText(paragraph.text, availableWidthPx, textMeasurer, textStyle, cancellationContext),
    )

    private fun measure(
        content: String,
        availableWidthPx: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        paragraphSpacingPx: Float,
        cancellationContext: CoroutineContext,
        paragraphMeasurements: MutableMap<Int, ReaderParagraphMeasurement>?,
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
            val layoutResult = measureText(
                paragraph.text, availableWidthPx, textMeasurer, textStyle, cancellationContext,
            )
            repeat(layoutResult.lineCount) { lineIndex ->
                lines += ReaderPageLine(
                    startCharOffset = paragraph.startCharOffset + layoutResult.getLineStart(lineIndex),
                    endCharOffset = paragraph.startCharOffset + layoutResult.getLineEnd(lineIndex, visibleEnd = true),
                    topPx = paragraphTopPx + layoutResult.getLineTop(lineIndex),
                    bottomPx = paragraphTopPx + layoutResult.getLineBottom(lineIndex),
                )
            }
            if (paragraphMeasurements != null) {
                paragraphMeasurements[paragraph.startCharOffset] = measurement(paragraph, layoutResult)
            }
            paragraphTopPx += layoutResult.size.height.toFloat()
            if (paragraphIndex < paragraphs.lastIndex) {
                paragraphTopPx += paragraphSpacingPx
            }
        }
        cancellationContext.ensureActive()
        return lines
    }

    private fun measurement(
        paragraph: ReaderDisplayParagraph,
        layout: TextLayoutResult,
    ) = ReaderParagraphMeasurement(
        startCharOffset = paragraph.startCharOffset,
        endCharOffset = paragraph.endCharOffset,
        layoutHeightPx = layout.size.height,
        lastLineBottomPx = layout.getLineBottom(layout.lineCount - 1),
    )

    private fun measureText(
        text: String,
        availableWidthPx: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        cancellationContext: CoroutineContext,
    ): TextLayoutResult {
        cancellationContext.ensureActive()
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = textStyle,
            overflow = TextOverflow.Clip,
            softWrap = true,
            constraints = Constraints(maxWidth = availableWidthPx.coerceAtLeast(1)),
        )
        cancellationContext.ensureActive()
        return layout
    }
}
