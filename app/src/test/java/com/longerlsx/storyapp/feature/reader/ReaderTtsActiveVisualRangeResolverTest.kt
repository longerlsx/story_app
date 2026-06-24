package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsActiveVisualRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsPlaybackSnapshot
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTtsActiveVisualRangeResolverTest {

    @Test
    fun exposesLiveRangeOnlyWhileCurrentBookIsSpeaking() {
        val liveRange = ReaderTtsActiveVisualRangeResolver.resolveLiveRange(
            runtimeState = runtimeStateWithLiveRange(),
            currentBookSession = ReaderTtsCurrentBookSession(
                isSpeaking = true,
                isPaused = false,
            ),
        )

        assertEquals(
            ReaderTtsActiveVisualRange(
                chapterIndex = 3,
                startCharOffset = 44,
                endCharOffset = 52,
            ),
            liveRange,
        )
    }

    @Test
    fun suppressesLiveRangeWhileCurrentBookIsPaused() {
        val liveRange = ReaderTtsActiveVisualRangeResolver.resolveLiveRange(
            runtimeState = runtimeStateWithLiveRange(),
            currentBookSession = ReaderTtsCurrentBookSession(
                isSpeaking = false,
                isPaused = true,
            ),
        )

        assertNull(liveRange)
    }

    @Test
    fun suppressesLiveRangeForNonCurrentBookSession() {
        val liveRange = ReaderTtsActiveVisualRangeResolver.resolveLiveRange(
            runtimeState = runtimeStateWithLiveRange(),
            currentBookSession = ReaderTtsCurrentBookSession(
                isSpeaking = false,
                isPaused = false,
            ),
        )

        assertNull(liveRange)
    }

    @Test
    fun pendingRestartRangeOverridesLivePlaybackRange() {
        val pendingRange = ReaderTtsActiveVisualRange(
            chapterIndex = 4,
            startCharOffset = 70,
            endCharOffset = 78,
        )

        val activeRange = ReaderTtsActiveVisualRangeResolver.resolveActiveRange(
            pendingRange = pendingRange,
            liveRange = ReaderTtsActiveVisualRange(
                chapterIndex = 3,
                startCharOffset = 44,
                endCharOffset = 52,
            ),
        )

        assertEquals(pendingRange, activeRange)
    }

    @Test
    fun fallsBackToLiveRangeWhenThereIsNoPendingRestartRange() {
        val liveRange = ReaderTtsActiveVisualRange(
            chapterIndex = 3,
            startCharOffset = 44,
            endCharOffset = 52,
        )

        val activeRange = ReaderTtsActiveVisualRangeResolver.resolveActiveRange(
            pendingRange = null,
            liveRange = liveRange,
        )

        assertEquals(liveRange, activeRange)
    }

    private fun runtimeStateWithLiveRange(): ReaderTtsRuntimeState {
        return ReaderTtsRuntimeState(
            playbackSnapshot = ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(
                    chapterIndex = 3,
                    startCharOffset = 40,
                    endCharOffset = 60,
                    spokenText = "这是当前朗读句子。",
                ),
                lastConfirmedSpokenRange = ReaderTtsCharacterRange(
                    startCharOffset = 44,
                    endCharOffset = 52,
                ),
            ),
        )
    }
}
