package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageAnchorMapperTest {

    @Test
    fun resolvesPageIndexFromSavedCharOffset() {
        val pages = listOf(
            ReaderPageSlice(startCharOffset = 0, endCharOffset = 100),
            ReaderPageSlice(startCharOffset = 100, endCharOffset = 220),
            ReaderPageSlice(startCharOffset = 220, endCharOffset = 360),
        )

        assertEquals(0, ReaderPageAnchorMapper.pageIndexForCharOffset(pages, 0))
        assertEquals(1, ReaderPageAnchorMapper.pageIndexForCharOffset(pages, 150))
        assertEquals(2, ReaderPageAnchorMapper.pageIndexForCharOffset(pages, 999))
    }

    @Test
    fun usesPageStartOffsetAsPersistedAnchor() {
        val pages = listOf(
            ReaderPageSlice(startCharOffset = 0, endCharOffset = 100),
            ReaderPageSlice(startCharOffset = 100, endCharOffset = 220),
        )

        assertEquals(100, ReaderPageAnchorMapper.anchorForPageIndex(pages, 1))
    }

    @Test
    fun mapsVisiblePageTextOffsetsBackToRawChapterOffsets() {
        val page = ReaderPageSlice(
            startCharOffset = 40,
            endCharOffset = 72,
            text = "正文段落",
            visibleStartCharOffset = 43,
            visibleEndCharOffset = 47,
        )

        assertEquals(43, page.rawCharOffsetForVisibleTextOffset(0))
        assertEquals(45, page.rawCharOffsetForVisibleTextOffset(2))
        assertEquals(46, page.rawCharOffsetForVisibleTextOffset(99))
    }
}
