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
}
