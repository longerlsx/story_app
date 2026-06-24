package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageBoundaryTransitionClearancePolicyTest {

    @Test
    fun doesNotClearWhenThereIsNoActiveTransition() {
        assertFalse(
            ReaderPageBoundaryTransitionClearancePolicy.shouldClear(
                transitionTargetChapterIndex = null,
                currentChapterIndex = 3,
                contentLoaded = true,
                restoredPosition = true,
            ),
        )
    }

    @Test
    fun doesNotClearBeforeTheTargetChapterIsCurrent() {
        assertFalse(
            ReaderPageBoundaryTransitionClearancePolicy.shouldClear(
                transitionTargetChapterIndex = 4,
                currentChapterIndex = 3,
                contentLoaded = true,
                restoredPosition = true,
            ),
        )
    }

    @Test
    fun doesNotClearUntilTargetContentIsLoadedAndRestored() {
        assertFalse(
            ReaderPageBoundaryTransitionClearancePolicy.shouldClear(
                transitionTargetChapterIndex = 4,
                currentChapterIndex = 4,
                contentLoaded = false,
                restoredPosition = true,
            ),
        )
        assertFalse(
            ReaderPageBoundaryTransitionClearancePolicy.shouldClear(
                transitionTargetChapterIndex = 4,
                currentChapterIndex = 4,
                contentLoaded = true,
                restoredPosition = false,
            ),
        )
    }

    @Test
    fun clearsWhenTargetChapterIsLoadedAndRestored() {
        assertTrue(
            ReaderPageBoundaryTransitionClearancePolicy.shouldClear(
                transitionTargetChapterIndex = 4,
                currentChapterIndex = 4,
                contentLoaded = true,
                restoredPosition = true,
            ),
        )
    }
}
