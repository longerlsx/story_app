package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageTapGesturePolicyTest {

    @Test
    fun tracksMovementOnlyAfterDistanceExceedsTouchSlop() {
        assertFalse(
            ReaderPageTapGesturePolicy.hasMovedBeyondTapSlop(
                wasMovedBeyondTapSlop = false,
                distanceFromDownPx = 12f,
                touchSlopPx = 12f,
            ),
        )
        assertTrue(
            ReaderPageTapGesturePolicy.hasMovedBeyondTapSlop(
                wasMovedBeyondTapSlop = false,
                distanceFromDownPx = 12.1f,
                touchSlopPx = 12f,
            ),
        )
    }

    @Test
    fun keepsMovementMarkedAfterItWasExceeded() {
        assertTrue(
            ReaderPageTapGesturePolicy.hasMovedBeyondTapSlop(
                wasMovedBeyondTapSlop = true,
                distanceFromDownPx = 0f,
                touchSlopPx = 12f,
            ),
        )
    }

    @Test
    fun ignoresTapZoneAfterMovementExceeded() {
        assertNull(
            ReaderPageTapGesturePolicy.resolveTapZoneOrNull(
                movedBeyondTapSlop = true,
                upX = 150f,
                width = 300f,
            ),
        )
    }

    @Test
    fun resolvesTapZoneForStableTapFromUpPosition() {
        assertEquals(
            ReaderTapZone.PREVIOUS,
            ReaderPageTapGesturePolicy.resolveTapZoneOrNull(
                movedBeyondTapSlop = false,
                upX = 50f,
                width = 300f,
            ),
        )
        assertEquals(
            ReaderTapZone.TOGGLE_CHROME,
            ReaderPageTapGesturePolicy.resolveTapZoneOrNull(
                movedBeyondTapSlop = false,
                upX = 150f,
                width = 300f,
            ),
        )
        assertEquals(
            ReaderTapZone.NEXT,
            ReaderPageTapGesturePolicy.resolveTapZoneOrNull(
                movedBeyondTapSlop = false,
                upX = 260f,
                width = 300f,
            ),
        )
    }
}
