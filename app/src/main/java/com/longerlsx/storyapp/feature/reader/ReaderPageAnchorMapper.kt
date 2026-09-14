package com.longerlsx.storyapp.feature.reader

data class ReaderPageSlice(
    val startCharOffset: Int,
    val endCharOffset: Int,
    val text: String = "",
    val rawText: String = text,
    val visibleStartCharOffset: Int = startCharOffset,
    val visibleEndCharOffset: Int = endCharOffset,
)

internal fun readerPageSliceFromRawText(
    startCharOffset: Int,
    endCharOffset: Int,
    rawText: String,
): ReaderPageSlice {
    val trimmedPrefixLength = rawText.takeWhile { it == '\n' }.length
    val trimmedSuffixLength = rawText.takeLastWhile { it == '\n' }.length
    val visibleStartCharOffset = (startCharOffset + trimmedPrefixLength).coerceAtMost(endCharOffset)
    val visibleEndCharOffset = (endCharOffset - trimmedSuffixLength).coerceAtLeast(visibleStartCharOffset)
    return ReaderPageSlice(
        startCharOffset = startCharOffset,
        endCharOffset = endCharOffset,
        text = rawText.trim('\n').ifBlank { rawText.ifBlank { "当前章节暂无正文。" } },
        rawText = rawText,
        visibleStartCharOffset = visibleStartCharOffset,
        visibleEndCharOffset = visibleEndCharOffset,
    )
}

fun ReaderPageSlice.rawCharOffsetForVisibleTextOffset(
    visibleTextOffset: Int,
): Int {
    val lastVisibleCharOffset = (visibleEndCharOffset - 1).coerceAtLeast(visibleStartCharOffset)
    return (visibleStartCharOffset + visibleTextOffset)
        .coerceIn(visibleStartCharOffset, lastVisibleCharOffset)
}

object ReaderPageAnchorMapper {
    fun pageIndexForCharOffset(
        pages: List<ReaderPageSlice>,
        charOffset: Int,
    ): Int {
        if (pages.isEmpty()) {
            return 0
        }

        // A page gap contains formatting whitespace. Restore the following
        // visible text, rather than falling through to the chapter's last page.
        return pages.indexOfFirst { charOffset < it.endCharOffset }
            .takeIf { it >= 0 }
            ?: pages.lastIndex
    }

    fun anchorForPageIndex(
        pages: List<ReaderPageSlice>,
        pageIndex: Int,
    ): Int {
        if (pages.isEmpty()) {
            return 0
        }

        return pages[pageIndex.coerceIn(0, pages.lastIndex)].visibleStartCharOffset
    }
}
