package com.longerlsx.storyapp.feature.reader

data class ReaderPageSlice(
    val startCharOffset: Int,
    val endCharOffset: Int,
    val text: String = "",
)

object ReaderPageAnchorMapper {
    fun pageIndexForCharOffset(
        pages: List<ReaderPageSlice>,
        charOffset: Int,
    ): Int {
        if (pages.isEmpty()) {
            return 0
        }

        val index = pages.indexOfFirst { page ->
            charOffset in page.startCharOffset until page.endCharOffset
        }
        return when {
            index >= 0 -> index
            charOffset < pages.first().startCharOffset -> 0
            else -> pages.lastIndex
        }
    }

    fun anchorForPageIndex(
        pages: List<ReaderPageSlice>,
        pageIndex: Int,
    ): Int {
        if (pages.isEmpty()) {
            return 0
        }

        return pages[pageIndex.coerceIn(0, pages.lastIndex)].startCharOffset
    }
}
