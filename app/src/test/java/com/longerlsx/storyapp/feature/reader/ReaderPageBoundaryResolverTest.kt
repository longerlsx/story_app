package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderPageBoundaryResolverTest {

    @Test
    fun ignoresTurnUntilPageContentIsReady() {
        val action = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 0,
            pageCount = 2,
            contentLoaded = false,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = null,
            nextChapterIndex = 2,
        )

        assertSame(ReaderPageTurnAction.Ignore, action)
    }

    @Test
    fun ignoresTurnWhilePagerIsStillScrolling() {
        val action = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 0,
            pageCount = 2,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = true,
            hasActiveBoundaryTransition = false,
            previousChapterIndex = null,
            nextChapterIndex = 2,
        )

        assertSame(ReaderPageTurnAction.Ignore, action)
    }

    @Test
    fun ignoresTurnWhileBoundaryTransitionIsActive() {
        val action = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 1,
            pageCount = 2,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            hasActiveBoundaryTransition = true,
            previousChapterIndex = 0,
            nextChapterIndex = 2,
        )

        assertSame(ReaderPageTurnAction.Ignore, action)
    }

    @Test
    fun ignoresTurnUntilPagePositionIsRestored() {
        val action = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 0,
            pageCount = 2,
            contentLoaded = true,
            restoredPosition = false,
            isScrollInProgress = false,
            previousChapterIndex = null,
            nextChapterIndex = 2,
        )

        assertSame(ReaderPageTurnAction.Ignore, action)
    }

    @Test
    fun ignoresTurnWhenThereAreNoPages() {
        val action = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 0,
            pageCount = 0,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = null,
            nextChapterIndex = 2,
        )

        assertSame(ReaderPageTurnAction.Ignore, action)
    }

    @Test
    fun turnsWithinCurrentChapterFromSettledPage() {
        val forward = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 1,
            pageCount = 3,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = 0,
            nextChapterIndex = 2,
        )
        val backward = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.PREVIOUS,
            settledPage = 1,
            pageCount = 3,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = 0,
            nextChapterIndex = 2,
        )

        assertEquals(ReaderPageTurnAction.Page(pageIndex = 2), forward)
        assertEquals(ReaderPageTurnAction.Page(pageIndex = 0), backward)
    }

    @Test
    fun clampsSettledPageBeforeResolvingTurn() {
        val forward = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 99,
            pageCount = 3,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = 4,
            nextChapterIndex = 6,
        )
        val backward = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.PREVIOUS,
            settledPage = -4,
            pageCount = 3,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = 4,
            nextChapterIndex = 6,
        )

        assertEquals(
            ReaderPageTurnAction.Chapter(chapterIndex = 6, restoreToLastPage = false),
            forward,
        )
        assertEquals(
            ReaderPageTurnAction.Chapter(chapterIndex = 4, restoreToLastPage = true),
            backward,
        )
    }

    @Test
    fun opensExplicitAdjacentChapterAtSettledPageBoundary() {
        val forward = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 2,
            pageCount = 3,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = 4,
            nextChapterIndex = 6,
        )
        val backward = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.PREVIOUS,
            settledPage = 0,
            pageCount = 3,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = 4,
            nextChapterIndex = 6,
        )

        assertEquals(
            ReaderPageTurnAction.Chapter(chapterIndex = 6, restoreToLastPage = false),
            forward,
        )
        assertEquals(
            ReaderPageTurnAction.Chapter(chapterIndex = 4, restoreToLastPage = true),
            backward,
        )
    }

    @Test
    fun ignoresBoundaryTurnWhenAdjacentChapterIsMissing() {
        val forward = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 2,
            pageCount = 3,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = 4,
            nextChapterIndex = null,
        )
        val backward = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = ReaderPageTurnDirection.PREVIOUS,
            settledPage = 0,
            pageCount = 3,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            previousChapterIndex = null,
            nextChapterIndex = 6,
        )

        assertSame(ReaderPageTurnAction.Ignore, forward)
        assertSame(ReaderPageTurnAction.Ignore, backward)
    }
}
