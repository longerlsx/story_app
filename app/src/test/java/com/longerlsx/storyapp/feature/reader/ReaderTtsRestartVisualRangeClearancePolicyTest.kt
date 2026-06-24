package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsActiveVisualRange
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsRestartVisualRangeClearancePolicyTest {

    @Test
    fun clearsPendingRangeWhenCurrentBookSessionIsNoLongerOngoing() {
        assertTrue(
            ReaderTtsRestartVisualRangeClearancePolicy.shouldClearPendingRange(
                pendingRange = pendingRange(),
                liveRange = null,
                currentBookSession = ReaderTtsCurrentBookSession(
                    isSpeaking = false,
                    isPaused = false,
                ),
            ),
        )
    }

    @Test
    fun keepsPendingRangeWhileOngoingPlaybackHasNoLiveRangeYet() {
        assertFalse(
            ReaderTtsRestartVisualRangeClearancePolicy.shouldClearPendingRange(
                pendingRange = pendingRange(),
                liveRange = null,
                currentBookSession = ReaderTtsCurrentBookSession(
                    isSpeaking = true,
                    isPaused = false,
                ),
            ),
        )
    }

    @Test
    fun clearsPendingRangeWhenLiveRangeCoversTheRestartStartOffset() {
        assertTrue(
            ReaderTtsRestartVisualRangeClearancePolicy.shouldClearPendingRange(
                pendingRange = pendingRange(start = 42),
                liveRange = liveRange(start = 40, end = 48),
                currentBookSession = ReaderTtsCurrentBookSession(
                    isSpeaking = true,
                    isPaused = false,
                ),
            ),
        )
    }

    @Test
    fun keepsPendingRangeWhenLiveRangeIsBeforeTheRestartStartOffset() {
        assertFalse(
            ReaderTtsRestartVisualRangeClearancePolicy.shouldClearPendingRange(
                pendingRange = pendingRange(start = 42),
                liveRange = liveRange(start = 30, end = 41),
                currentBookSession = ReaderTtsCurrentBookSession(
                    isSpeaking = true,
                    isPaused = false,
                ),
            ),
        )
    }

    @Test
    fun keepsPendingRangeWhenLiveRangeBelongsToAnotherChapter() {
        assertFalse(
            ReaderTtsRestartVisualRangeClearancePolicy.shouldClearPendingRange(
                pendingRange = pendingRange(chapterIndex = 2, start = 42),
                liveRange = liveRange(chapterIndex = 3, start = 40, end = 48),
                currentBookSession = ReaderTtsCurrentBookSession(
                    isSpeaking = true,
                    isPaused = false,
                ),
            ),
        )
    }

    private fun pendingRange(
        chapterIndex: Int = 2,
        start: Int = 42,
    ): ReaderTtsActiveVisualRange {
        return ReaderTtsActiveVisualRange(
            chapterIndex = chapterIndex,
            startCharOffset = start,
            endCharOffset = start + 8,
        )
    }

    private fun liveRange(
        chapterIndex: Int = 2,
        start: Int,
        end: Int,
    ): ReaderTtsActiveVisualRange {
        return ReaderTtsActiveVisualRange(
            chapterIndex = chapterIndex,
            startCharOffset = start,
            endCharOffset = end,
        )
    }
}
