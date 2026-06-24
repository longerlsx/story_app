package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageBoundaryTransitionResolverTest {

    @Test
    fun nextBoundaryUsesCurrentSettledPageAndNextChapterFirstPage() {
        val currentPages = listOf(page(0), page(100), page(200))
        val nextPages = listOf(page(300), page(400))

        val transition = ReaderPageBoundaryTransitionResolver.resolve(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 2,
            currentPages = currentPages,
            previousPages = emptyList(),
            nextPages = nextPages,
            targetChapterIndex = 6,
        )

        assertEquals(
            ReaderBoundaryPageTransition(
                direction = ReaderPageTurnDirection.NEXT,
                sourcePage = currentPages[2],
                previewPage = nextPages.first(),
                targetChapterIndex = 6,
            ),
            transition,
        )
    }

    @Test
    fun previousBoundaryUsesCurrentSettledPageAndPreviousChapterLastPage() {
        val currentPages = listOf(page(200), page(300))
        val previousPages = listOf(page(0), page(100))

        val transition = ReaderPageBoundaryTransitionResolver.resolve(
            direction = ReaderPageTurnDirection.PREVIOUS,
            settledPage = 0,
            currentPages = currentPages,
            previousPages = previousPages,
            nextPages = emptyList(),
            targetChapterIndex = 4,
        )

        assertEquals(
            ReaderBoundaryPageTransition(
                direction = ReaderPageTurnDirection.PREVIOUS,
                sourcePage = currentPages.first(),
                previewPage = previousPages.last(),
                targetChapterIndex = 4,
            ),
            transition,
        )
    }

    @Test
    fun clampsSettledPageBeforeChoosingSourcePage() {
        val currentPages = listOf(page(0), page(100), page(200))
        val nextPages = listOf(page(300))

        val transition = ReaderPageBoundaryTransitionResolver.resolve(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 99,
            currentPages = currentPages,
            previousPages = emptyList(),
            nextPages = nextPages,
            targetChapterIndex = 6,
        )

        assertEquals(currentPages.last(), transition?.sourcePage)
    }

    @Test
    fun returnsNullWhenSourceOrPreviewPageIsMissing() {
        val currentPages = listOf(page(0))

        assertNull(
            ReaderPageBoundaryTransitionResolver.resolve(
                direction = ReaderPageTurnDirection.NEXT,
                settledPage = 0,
                currentPages = emptyList(),
                previousPages = emptyList(),
                nextPages = listOf(page(100)),
                targetChapterIndex = 6,
            ),
        )
        assertNull(
            ReaderPageBoundaryTransitionResolver.resolve(
                direction = ReaderPageTurnDirection.NEXT,
                settledPage = 0,
                currentPages = currentPages,
                previousPages = emptyList(),
                nextPages = emptyList(),
                targetChapterIndex = 6,
            ),
        )
        assertNull(
            ReaderPageBoundaryTransitionResolver.resolve(
                direction = ReaderPageTurnDirection.PREVIOUS,
                settledPage = 0,
                currentPages = currentPages,
                previousPages = emptyList(),
                nextPages = listOf(page(100)),
                targetChapterIndex = 4,
            ),
        )
    }

    private fun page(
        startCharOffset: Int,
    ): ReaderPageSlice {
        return ReaderPageSlice(
            startCharOffset = startCharOffset,
            endCharOffset = startCharOffset + 100,
            text = "page-$startCharOffset",
        )
    }
}
