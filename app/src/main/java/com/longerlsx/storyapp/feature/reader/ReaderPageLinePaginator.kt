package com.longerlsx.storyapp.feature.reader

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
    ): List<ReaderPageSlice> {
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
                candidateLineIndex -= 1
                candidatePage = buildPageSlice(
                    content = content,
                    lines = lines,
                    startLineIndex = lineIndex,
                    endLineIndex = candidateLineIndex,
                )
            }

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
        )
    }
}
