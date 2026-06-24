package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsFollowSuppressionPolicyTest {

    @Test
    fun suppressesFollowForTenSecondsAfterInterruption() {
        assertTrue(
            ReaderTtsFollowSuppressionPolicy.shouldSuppressFollow(
                lastInterruptionAtMs = 1_000L,
                nowMs = 10_999L,
            ),
        )
        assertFalse(
            ReaderTtsFollowSuppressionPolicy.shouldSuppressFollow(
                lastInterruptionAtMs = 1_000L,
                nowMs = 11_000L,
            ),
        )
    }

    @Test
    fun reportsRemainingSuppressionDelayUntilTheWindowExpires() {
        assertEquals(
            1L,
            ReaderTtsFollowSuppressionPolicy.remainingSuppressionMillis(
                lastInterruptionAtMs = 1_000L,
                nowMs = 10_999L,
            ),
        )
        assertEquals(
            0L,
            ReaderTtsFollowSuppressionPolicy.remainingSuppressionMillis(
                lastInterruptionAtMs = 1_000L,
                nowMs = 11_000L,
            ),
        )
        assertEquals(
            null,
            ReaderTtsFollowSuppressionPolicy.remainingSuppressionMillis(
                lastInterruptionAtMs = null,
                nowMs = 11_000L,
            ),
        )
    }

    @Test
    fun clearsOnlyTheScheduledExpiredInterruption() {
        assertTrue(
            ReaderTtsFollowSuppressionPolicy.shouldClearExpiredSuppression(
                scheduledInterruptionAtMs = 1_000L,
                currentInterruptionAtMs = 1_000L,
                nowMs = 11_000L,
            ),
        )
        assertFalse(
            ReaderTtsFollowSuppressionPolicy.shouldClearExpiredSuppression(
                scheduledInterruptionAtMs = 1_000L,
                currentInterruptionAtMs = 1_000L,
                nowMs = 10_999L,
            ),
        )
        assertFalse(
            ReaderTtsFollowSuppressionPolicy.shouldClearExpiredSuppression(
                scheduledInterruptionAtMs = 1_000L,
                currentInterruptionAtMs = 2_000L,
                nowMs = 11_000L,
            ),
        )
    }
}
