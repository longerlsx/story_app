package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChromeAutoHidePolicyTest {

    @Test
    fun doesNotScheduleAutoHideWithoutInteractionTime() {
        assertEquals(
            null,
            ReaderChromeAutoHidePolicy.remainingDelayMillis(
                chromeMode = ReaderChromeMode.CHROME_VISIBLE,
                lastInteractionAtMs = null,
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun doesNotScheduleAutoHideOutsideChromeVisibleMode() {
        assertEquals(
            null,
            ReaderChromeAutoHidePolicy.remainingDelayMillis(
                chromeMode = ReaderChromeMode.SETTINGS_EXPANDED,
                lastInteractionAtMs = 1_000L,
                nowMs = 2_000L,
            ),
        )
    }

    @Test
    fun clampsRemainingDelayAtZeroAfterTimeout() {
        assertEquals(
            0L,
            ReaderChromeAutoHidePolicy.remainingDelayMillis(
                chromeMode = ReaderChromeMode.CHROME_VISIBLE,
                lastInteractionAtMs = 1_000L,
                nowMs = 4_500L,
            ),
        )
    }

    @Test
    fun hidesOnlyWhenTheSameInteractionIsStillCurrent() {
        assertFalse(
            ReaderChromeAutoHidePolicy.shouldHideAfterDelay(
                chromeMode = ReaderChromeMode.CHROME_VISIBLE,
                scheduledInteractionAtMs = 1_000L,
                currentInteractionAtMs = 2_000L,
            ),
        )
        assertTrue(
            ReaderChromeAutoHidePolicy.shouldHideAfterDelay(
                chromeMode = ReaderChromeMode.CHROME_VISIBLE,
                scheduledInteractionAtMs = 1_000L,
                currentInteractionAtMs = 1_000L,
            ),
        )
    }
}
