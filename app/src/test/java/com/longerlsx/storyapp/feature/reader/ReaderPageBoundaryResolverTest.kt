package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageBoundaryResolverTest {

    @Test
    fun returnsPreviousChapterLastPageAnchor() {
        val pages = listOf(
            ReaderPageSlice(startCharOffset = 0, endCharOffset = 120),
            ReaderPageSlice(startCharOffset = 120, endCharOffset = 260),
            ReaderPageSlice(startCharOffset = 260, endCharOffset = 400),
        )

        val target = ReaderPageBoundaryResolver.previousChapterTarget(
            previousChapterIndex = 8,
            previousChapterPages = pages,
        )

        assertEquals(8, target.chapterIndex)
        assertEquals(260, target.charOffset)
    }

    @Test
    fun fallsBackToChapterStartWhenPreviousChapterHasNoPages() {
        val target = ReaderPageBoundaryResolver.previousChapterTarget(
            previousChapterIndex = 3,
            previousChapterPages = emptyList(),
        )

        assertEquals(3, target.chapterIndex)
        assertEquals(0, target.charOffset)
    }
}
