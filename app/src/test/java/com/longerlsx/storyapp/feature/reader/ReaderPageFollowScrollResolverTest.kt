package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageFollowScrollResolverTest {

    @Test
    fun returnsNullWhenFollowTargetIsMissing() {
        val pages = listOf(page(0), page(100))

        assertNull(
            ReaderPageFollowScrollResolver.resolveTargetPage(
                pages = pages,
                followTargetCharOffset = null,
                currentPage = 0,
                restoredPosition = true,
            ),
        )
    }

    @Test
    fun returnsNullUntilReaderPositionIsRestored() {
        val pages = listOf(page(0), page(100))

        assertNull(
            ReaderPageFollowScrollResolver.resolveTargetPage(
                pages = pages,
                followTargetCharOffset = 120,
                currentPage = 0,
                restoredPosition = false,
            ),
        )
    }

    @Test
    fun returnsNullWhenThereAreNoPages() {
        assertNull(
            ReaderPageFollowScrollResolver.resolveTargetPage(
                pages = emptyList(),
                followTargetCharOffset = 120,
                currentPage = 0,
                restoredPosition = true,
            ),
        )
    }

    @Test
    fun returnsTargetPageWhenFollowTargetIsOnDifferentPage() {
        val pages = listOf(page(0), page(100), page(200))

        assertEquals(
            1,
            ReaderPageFollowScrollResolver.resolveTargetPage(
                pages = pages,
                followTargetCharOffset = 120,
                currentPage = 0,
                restoredPosition = true,
            ),
        )
    }

    @Test
    fun returnsNullWhenCurrentPageAlreadyContainsFollowTarget() {
        val pages = listOf(page(0), page(100), page(200))

        assertNull(
            ReaderPageFollowScrollResolver.resolveTargetPage(
                pages = pages,
                followTargetCharOffset = 120,
                currentPage = 1,
                restoredPosition = true,
            ),
        )
    }

    @Test
    fun resolvesOutOfRangeFollowTargetToLastPage() {
        val pages = listOf(page(0), page(100), page(200))

        assertEquals(
            2,
            ReaderPageFollowScrollResolver.resolveTargetPage(
                pages = pages,
                followTargetCharOffset = 999,
                currentPage = 0,
                restoredPosition = true,
            ),
        )
    }

    private fun page(
        startCharOffset: Int,
    ): ReaderPageSlice {
        return ReaderPageSlice(
            startCharOffset = startCharOffset,
            endCharOffset = startCharOffset + 100,
        )
    }
}
