package com.longerlsx.storyapp.feature.reader.tts

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
}
