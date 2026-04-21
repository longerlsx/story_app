package com.longerlsx.storyapp.feature.reader

data class ReaderVisibleChapterItem(
    val itemIndex: Int,
    val chapterIndex: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

object ReaderScrollChapterResolver {
    fun resolveActiveChapter(
        visibleItems: List<ReaderVisibleChapterItem>,
        viewportTopPx: Int,
    ): Int? {
        val activeItem = visibleItems
            .sortedBy { it.offsetPx }
            .firstOrNull { item ->
                item.offsetPx + item.sizePx > viewportTopPx
            }
        return activeItem?.chapterIndex
    }
}
