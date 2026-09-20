package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun paginateMeasuredPageSlices(
    content: String,
    availableWidthPx: Int,
    availableHeightPx: Int,
    paragraphSpacingPx: Float,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    cancellationContext: CoroutineContext = EmptyCoroutineContext,
): List<ReaderPageSlice> {
    cancellationContext.ensureActive()
    if (content.isBlank() || availableWidthPx <= 0) {
        return listOf(ReaderPageSlice(0, 0, text = "当前章节暂无正文。"))
    }
    val chapter = ReaderPageTextLayout.measureChapter(
        content, availableWidthPx, textMeasurer, textStyle, paragraphSpacingPx, cancellationContext,
    )
    val pageFit = ReaderMeasuredPageFit(
        chapter, availableWidthPx, availableHeightPx, paragraphSpacingPx,
        textMeasurer, textStyle, cancellationContext,
    )
    return ReaderPageLinePaginator.paginate(
        content = content,
        lines = chapter.lines,
        availableHeightPx = availableHeightPx.coerceAtLeast(1).toFloat(),
        pageFitsViewport = pageFit::fits,
        cancellationContext = cancellationContext,
    )
}

/** Owned by one pagination call, with exactly the same text and layout inputs as [chapter]. */
internal class ReaderMeasuredPageFit(
    private val chapter: ReaderMeasuredChapterText,
    private val availableWidthPx: Int,
    private val availableHeightPx: Int,
    private val paragraphSpacingPx: Float,
    private val textMeasurer: TextMeasurer,
    private val textStyle: TextStyle,
    private val cancellationContext: CoroutineContext = EmptyCoroutineContext,
) {
    private var pageStart = -1
    private var firstFragment: ReaderParagraphMeasurement? = null

    fun fits(page: ReaderPageSlice): Boolean {
        cancellationContext.ensureActive()
        if (page.startCharOffset != pageStart) {
            pageStart = page.startCharOffset
            firstFragment = null
        }
        if (page.rawText.isBlank()) return true

        // Match the actual standalone page's whitespace normalization, not the chapter's lines.
        val paragraphs = ReaderParagraphModel.toDisplayParagraphs(page.rawText)
        var paragraphTop = 0f
        var pageBottom = 0f
        paragraphs.forEachIndexed { index, paragraph ->
            cancellationContext.ensureActive()
            val start = page.startCharOffset + paragraph.startCharOffset
            val end = page.startCharOffset + paragraph.endCharOffset
            val fullParagraph = chapter.paragraphsByStart[start]?.takeIf { it.endCharOffset == end }
            val reusablePrefix = firstFragment?.takeIf {
                index == 0 && it.startCharOffset == start && it.endCharOffset == end
            }
            val measured = fullParagraph ?: reusablePrefix ?: ReaderPageTextLayout.measureParagraph(
                paragraph = paragraph.copy(startCharOffset = start, endCharOffset = end),
                availableWidthPx = availableWidthPx,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                cancellationContext = cancellationContext,
            ).also {
                // Only the leading fragment can stay unchanged while the candidate's tail backs off.
                // A single-paragraph candidate changes its end on every backoff and is never retained.
                if (index == 0 && index < paragraphs.lastIndex) firstFragment = it
            }

            pageBottom = paragraphTop + measured.lastLineBottomPx
            // Preserve the rendering order and integer layout height: global chapter coordinates or
            // a sum of line heights lose page-local rounding and can accept a clipped final line.
            paragraphTop += measured.layoutHeightPx.toFloat()
            if (index < paragraphs.lastIndex) paragraphTop += paragraphSpacingPx
        }
        cancellationContext.ensureActive()
        return pageBottom <= availableHeightPx
    }
}
