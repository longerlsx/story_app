package com.longerlsx.storyapp.feature.reader

data class ReaderScrollBodyMetrics(
    val topWithinItemPx: Int,
    val heightPx: Int,
    val lines: List<ReaderPageLine> = emptyList(),
)

data class ReaderRawVisibleScrollItem(
    val itemIndex: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

object ReaderScrollVisibleItemBuilder {
    fun build(
        rawItems: List<ReaderRawVisibleScrollItem>,
        chapterIndexByItemIndex: Map<Int, Int>,
        bodyMetricsByChapter: Map<Int, ReaderScrollBodyMetrics>,
    ): List<ReaderVisibleChapterItem> {
        return rawItems.mapNotNull { item ->
            val chapterIndex = chapterIndexByItemIndex[item.itemIndex] ?: return@mapNotNull null
            val bodyMetrics = bodyMetricsByChapter[chapterIndex]
            val bodyOffsetWithinItemPx = bodyMetrics
                ?.topWithinItemPx
                ?.coerceIn(0, item.sizePx.coerceAtLeast(0))
                ?: 0
            val maxBodyHeightPx = (item.sizePx - bodyOffsetWithinItemPx).coerceAtLeast(1)
            val bodyHeightPx = bodyMetrics
                ?.heightPx
                ?.coerceIn(1, maxBodyHeightPx)
                ?: item.sizePx.coerceAtLeast(1)
            ReaderVisibleChapterItem(
                itemIndex = item.itemIndex,
                chapterIndex = chapterIndex,
                offsetPx = item.offsetPx,
                sizePx = item.sizePx,
                bodyOffsetPx = item.offsetPx + bodyOffsetWithinItemPx,
                bodyHeightPx = bodyHeightPx,
            )
        }
    }
}
