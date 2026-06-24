package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageRestoreTargetResolverTest {

    @Test
    fun restoresToPageOwningCharOffset() {
        val pages = listOf(
            ReaderPageSlice(startCharOffset = 0, endCharOffset = 80),
            ReaderPageSlice(startCharOffset = 80, endCharOffset = 160),
            ReaderPageSlice(startCharOffset = 160, endCharOffset = 240),
        )

        val targetPage = ReaderPageRestoreTargetResolver.resolve(
            pages = pages,
            restoreCharOffset = 120,
            restoreToLastPage = false,
        )

        assertEquals(1, targetPage)
    }

    @Test
    fun restoreToLastPageOverridesCharOffset() {
        val pages = listOf(
            ReaderPageSlice(startCharOffset = 0, endCharOffset = 80),
            ReaderPageSlice(startCharOffset = 80, endCharOffset = 160),
            ReaderPageSlice(startCharOffset = 160, endCharOffset = 240),
        )

        val targetPage = ReaderPageRestoreTargetResolver.resolve(
            pages = pages,
            restoreCharOffset = 0,
            restoreToLastPage = true,
        )

        assertEquals(2, targetPage)
    }

    @Test
    fun emptyPagesFallbackToFirstPageIndex() {
        val targetPage = ReaderPageRestoreTargetResolver.resolve(
            pages = emptyList(),
            restoreCharOffset = 120,
            restoreToLastPage = true,
        )

        assertEquals(0, targetPage)
    }
}
