package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageAnchorGapTest {
    @Test
    fun whitespaceBetweenPagesRestoresTheFollowingPageInsteadOfChapterEnd() {
        val pages = listOf(
            ReaderPageSlice(0, 5, "甲甲甲甲甲"),
            ReaderPageSlice(7, 12, "乙乙乙乙乙"),
            ReaderPageSlice(14, 19, "丙丙丙丙丙"),
        )
        assertEquals(1, ReaderPageAnchorMapper.pageIndexForCharOffset(pages, 6))
        assertEquals(2, ReaderPageAnchorMapper.pageIndexForCharOffset(pages, 13))
        assertEquals(2, ReaderPageAnchorMapper.pageIndexForCharOffset(pages, 19))
    }
}
