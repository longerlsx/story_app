package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageBoundaryTransitionOffsetResolverTest {

    @Test
    fun nextTransitionMovesCurrentPageLeftAndPreviewPageInFromRight() {
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = 0, previewPageOffsetPx = 400),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.NEXT,
                containerWidthPx = 400,
                progress = 0f,
            ),
        )
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = -200, previewPageOffsetPx = 200),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.NEXT,
                containerWidthPx = 400,
                progress = 0.5f,
            ),
        )
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = -400, previewPageOffsetPx = 0),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.NEXT,
                containerWidthPx = 400,
                progress = 1f,
            ),
        )
    }

    @Test
    fun previousTransitionMovesCurrentPageRightAndPreviewPageInFromLeft() {
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = 0, previewPageOffsetPx = -400),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.PREVIOUS,
                containerWidthPx = 400,
                progress = 0f,
            ),
        )
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = 200, previewPageOffsetPx = -200),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.PREVIOUS,
                containerWidthPx = 400,
                progress = 0.5f,
            ),
        )
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = 400, previewPageOffsetPx = 0),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.PREVIOUS,
                containerWidthPx = 400,
                progress = 1f,
            ),
        )
    }

    @Test
    fun clampsProgressAndNegativeWidthToStableOffsets() {
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = 0, previewPageOffsetPx = 400),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.NEXT,
                containerWidthPx = 400,
                progress = -0.5f,
            ),
        )
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = 400, previewPageOffsetPx = 0),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.PREVIOUS,
                containerWidthPx = 400,
                progress = 1.5f,
            ),
        )
        assertEquals(
            ReaderPageBoundaryTransitionOffsets(currentPageOffsetPx = 0, previewPageOffsetPx = 0),
            ReaderPageBoundaryTransitionOffsetResolver.resolve(
                direction = ReaderPageTurnDirection.NEXT,
                containerWidthPx = -1,
                progress = 0.5f,
            ),
        )
    }
}
