package com.longerlsx.storyapp.feature.reader

data class ReaderVisibleChapterItem(
    val itemIndex: Int,
    val chapterIndex: Int,
    val offsetPx: Int,
    val sizePx: Int,
    val bodyOffsetPx: Int = offsetPx,
    val bodyHeightPx: Int = sizePx,
)

object ReaderScrollChapterResolver {
    fun resolveActiveChapter(
        visibleItems: List<ReaderVisibleChapterItem>,
        viewportTopPx: Int,
    ): Int? {
        val activeItem = visibleItems
            .sortedBy { it.bodyOffsetPx }
            .firstOrNull { item ->
                item.bodyOffsetPx + item.bodyHeightPx > viewportTopPx
            }
        return activeItem?.chapterIndex
    }
}
