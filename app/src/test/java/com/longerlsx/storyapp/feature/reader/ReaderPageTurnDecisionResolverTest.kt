package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderPageTurnDecisionResolverTest {

    @Test
    fun resolvesInChapterPageTurnWithoutBoundaryTransition() {
        val pages = listOf(page(0), page(100), page(200))

        val decision = ReaderPageTurnDecisionResolver.resolve(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 1,
            currentPages = pages,
            previousPages = emptyList(),
            nextPages = emptyList(),
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            hasActiveBoundaryTransition = false,
            previousChapterIndex = 0,
            nextChapterIndex = 2,
        )

        assertEquals(ReaderPageTurnAction.Page(pageIndex = 2), decision.action)
        assertNull(decision.boundaryTransition)
    }

    @Test
    fun resolvesNextChapterBoundaryWithPreviewTransition() {
        val currentPages = listOf(page(0), page(100))
        val nextPages = listOf(page(200), page(300))

        val decision = ReaderPageTurnDecisionResolver.resolve(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 1,
            currentPages = currentPages,
            previousPages = emptyList(),
            nextPages = nextPages,
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            hasActiveBoundaryTransition = false,
            previousChapterIndex = 0,
            nextChapterIndex = 2,
        )

        assertEquals(
            ReaderPageTurnAction.Chapter(chapterIndex = 2, restoreToLastPage = false),
            decision.action,
        )
        assertEquals(
            ReaderBoundaryPageTransition(
                direction = ReaderPageTurnDirection.NEXT,
                sourcePage = currentPages.last(),
                previewPage = nextPages.first(),
                targetChapterIndex = 2,
            ),
            decision.boundaryTransition,
        )
    }

    @Test
    fun resolvesPreviousChapterBoundaryWithPreviewTransition() {
        val previousPages = listOf(page(0), page(100))
        val currentPages = listOf(page(200), page(300))

        val decision = ReaderPageTurnDecisionResolver.resolve(
            direction = ReaderPageTurnDirection.PREVIOUS,
            settledPage = 0,
            currentPages = currentPages,
            previousPages = previousPages,
            nextPages = emptyList(),
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            hasActiveBoundaryTransition = false,
            previousChapterIndex = 4,
            nextChapterIndex = 6,
        )

        assertEquals(
            ReaderPageTurnAction.Chapter(chapterIndex = 4, restoreToLastPage = true),
            decision.action,
        )
        assertEquals(
            ReaderBoundaryPageTransition(
                direction = ReaderPageTurnDirection.PREVIOUS,
                sourcePage = currentPages.first(),
                previewPage = previousPages.last(),
                targetChapterIndex = 4,
            ),
            decision.boundaryTransition,
        )
    }

    @Test
    fun keepsChapterActionWhenPreviewPageIsUnavailable() {
        val currentPages = listOf(page(0), page(100))

        val decision = ReaderPageTurnDecisionResolver.resolve(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 1,
            currentPages = currentPages,
            previousPages = emptyList(),
            nextPages = emptyList(),
            contentLoaded = true,
            restoredPosition = true,
            isScrollInProgress = false,
            hasActiveBoundaryTransition = false,
            previousChapterIndex = 0,
            nextChapterIndex = 2,
        )

        assertEquals(
            ReaderPageTurnAction.Chapter(chapterIndex = 2, restoreToLastPage = false),
            decision.action,
        )
        assertNull(decision.boundaryTransition)
    }

    @Test
    fun ignoresTurnWithoutBoundaryTransitionWhenPageStateIsNotReady() {
        val decision = ReaderPageTurnDecisionResolver.resolve(
            direction = ReaderPageTurnDirection.NEXT,
            settledPage = 0,
            currentPages = listOf(page(0)),
            previousPages = emptyList(),
            nextPages = listOf(page(100)),
            contentLoaded = false,
            restoredPosition = true,
            isScrollInProgress = false,
            hasActiveBoundaryTransition = false,
            previousChapterIndex = null,
            nextChapterIndex = 2,
        )

        assertSame(ReaderPageTurnAction.Ignore, decision.action)
        assertNull(decision.boundaryTransition)
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
