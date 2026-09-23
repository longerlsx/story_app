package com.longerlsx.storyapp.feature.reader

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ensureActive

data class ReaderPageLine(
    val startCharOffset: Int,
    val endCharOffset: Int,
    val topPx: Float,
    val bottomPx: Float,
)

object ReaderPageLinePaginator {
    fun paginate(
        content: String,
        lines: List<ReaderPageLine>,
        availableHeightPx: Float,
        pageFitsViewport: (ReaderPageSlice) -> Boolean = { true },
        cancellationContext: CoroutineContext = EmptyCoroutineContext,
    ): List<ReaderPageSlice> {
        cancellationContext.ensureActive()
        if (content.isBlank() || lines.isEmpty()) {
            return listOf(
                ReaderPageSlice(
                    startCharOffset = 0,
                    endCharOffset = 0,
                    text = "当前章节暂无正文。",
                ),
            )
        }

        val pages = mutableListOf<ReaderPageSlice>()
        var lineIndex = 0
        while (lineIndex < lines.size) {
            cancellationContext.ensureActive()
            val pageStartLine = lines[lineIndex]
            val pageTop = pageStartLine.topPx
            val pageBottomLimit = pageTop + availableHeightPx.coerceAtLeast(1f)

            var lastVisibleLineIndex = lineIndex
            while (lastVisibleLineIndex < lines.lastIndex &&
                lines[lastVisibleLineIndex + 1].bottomPx <= pageBottomLimit
            ) {
                lastVisibleLineIndex += 1
            }

            var candidateLineIndex = lastVisibleLineIndex
            var candidatePage = buildPageSlice(
                content = content,
                lines = lines,
                startLineIndex = lineIndex,
                endLineIndex = candidateLineIndex,
            )
            while (
                candidateLineIndex > lineIndex &&
                !pageFitsViewport(candidatePage)
            ) {
                cancellationContext.ensureActive()
                candidateLineIndex -= 1
                candidatePage = buildPageSlice(
                    content = content,
                    lines = lines,
                    startLineIndex = lineIndex,
                    endLineIndex = candidateLineIndex,
                )
            }

            cancellationContext.ensureActive()
            pages += candidatePage
            lineIndex = candidateLineIndex + 1
        }

        return pages
    }

    private fun buildPageSlice(
        content: String,
        lines: List<ReaderPageLine>,
        startLineIndex: Int,
        endLineIndex: Int,
    ): ReaderPageSlice {
        val startCharOffset = lines[startLineIndex].startCharOffset
        val endCharOffset = lines[endLineIndex].endCharOffset
            .coerceAtLeast((startCharOffset + 1).coerceAtMost(content.length))
        val rawText = content.substring(startCharOffset, endCharOffset)
        return readerPageSliceFromRawText(
            startCharOffset = startCharOffset,
            endCharOffset = endCharOffset,
            rawText = rawText,
            firstParagraphContinues = continuesParagraph(content, startCharOffset),
        )
    }

    private fun continuesParagraph(content: String, startCharOffset: Int): Boolean {
        for (index in startCharOffset - 1 downTo 0) {
            if (content[index] == '\n') return false
            if (!content[index].isWhitespace()) return true
        }
        return false
    }
}
