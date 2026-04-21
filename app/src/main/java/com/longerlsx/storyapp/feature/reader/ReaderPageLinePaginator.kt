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

            val startCharOffset = pageStartLine.startCharOffset
            val endCharOffset = lines[lastVisibleLineIndex].endCharOffset
                .coerceAtLeast((startCharOffset + 1).coerceAtMost(content.length))
            val rawText = content.substring(startCharOffset, endCharOffset)
            pages += ReaderPageSlice(
                startCharOffset = startCharOffset,
                endCharOffset = endCharOffset,
                text = rawText.trim('\n').ifBlank { rawText.ifBlank { "当前章节暂无正文。" } },
            )
            lineIndex = lastVisibleLineIndex + 1
        }

        return pages
    }
}
