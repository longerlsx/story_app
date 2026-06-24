package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScrollVisibleItemBuilderTest {

    @Test
    fun appliesBodyMetricsInsideVisibleItem() {
        val result = ReaderScrollVisibleItemBuilder.build(
            rawItems = listOf(
                ReaderRawVisibleScrollItem(itemIndex = 0, offsetPx = -20, sizePx = 300),
            ),
            chapterIndexByItemIndex = mapOf(0 to 1),
            bodyMetricsByChapter = mapOf(
                1 to ReaderScrollBodyMetrics(topWithinItemPx = 80, heightPx = 160),
            ),
        )

        assertEquals(
            listOf(
                ReaderVisibleChapterItem(
                    itemIndex = 0,
                    chapterIndex = 1,
                    offsetPx = -20,
                    sizePx = 300,
                    bodyOffsetPx = 60,
                    bodyHeightPx = 160,
                ),
            ),
            result,
        )
    }

    @Test
    fun clampsBodyTopAndHeightToVisibleItemBounds() {
        val result = ReaderScrollVisibleItemBuilder.build(
            rawItems = listOf(
                ReaderRawVisibleScrollItem(itemIndex = 0, offsetPx = 10, sizePx = 120),
            ),
            chapterIndexByItemIndex = mapOf(0 to 2),
            bodyMetricsByChapter = mapOf(
                2 to ReaderScrollBodyMetrics(topWithinItemPx = 160, heightPx = 500),
            ),
        )

        assertEquals(
            listOf(
                ReaderVisibleChapterItem(
                    itemIndex = 0,
                    chapterIndex = 2,
                    offsetPx = 10,
                    sizePx = 120,
                    bodyOffsetPx = 130,
                    bodyHeightPx = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun fallsBackToWholeItemWhenBodyMetricsMissing() {
        val result = ReaderScrollVisibleItemBuilder.build(
            rawItems = listOf(
                ReaderRawVisibleScrollItem(itemIndex = 0, offsetPx = 15, sizePx = 180),
            ),
            chapterIndexByItemIndex = mapOf(0 to 3),
            bodyMetricsByChapter = emptyMap(),
        )

        assertEquals(
            listOf(
                ReaderVisibleChapterItem(
                    itemIndex = 0,
                    chapterIndex = 3,
                    offsetPx = 15,
                    sizePx = 180,
                    bodyOffsetPx = 15,
                    bodyHeightPx = 180,
                ),
            ),
            result,
        )
    }

    @Test
    fun skipsVisibleItemsWithoutFeedChapter() {
        val result = ReaderScrollVisibleItemBuilder.build(
            rawItems = listOf(
                ReaderRawVisibleScrollItem(itemIndex = 0, offsetPx = 0, sizePx = 100),
                ReaderRawVisibleScrollItem(itemIndex = 1, offsetPx = 100, sizePx = 100),
            ),
            chapterIndexByItemIndex = mapOf(1 to 4),
            bodyMetricsByChapter = emptyMap(),
        )

        assertEquals(
            listOf(
                ReaderVisibleChapterItem(itemIndex = 1, chapterIndex = 4, offsetPx = 100, sizePx = 100),
            ),
            result,
        )
    }
}
