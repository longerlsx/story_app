package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderTtsNotificationTextResolverTest {

    @Test
    fun timedPlaybackShowsSummaryAndRemainingTime() {
        val text = ReaderTtsNotificationTextResolver.resolveContentText(
            ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PLAYING,
                currentPlaybackSummary = "第三章",
                remainingTimerMillis = 28 * 60_000L,
                activeStateLabel = "朗读中",
            ),
        )

        assertEquals("第三章 · 28m", text)
    }

    @Test
    fun untimedPlaybackShowsStableActiveStateLabel() {
        val text = ReaderTtsNotificationTextResolver.resolveContentText(
            ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PLAYING,
                currentPlaybackSummary = "第三章",
                remainingTimerMillis = null,
                activeStateLabel = "朗读中",
            ),
        )

        assertEquals("第三章 · 朗读中", text)
    }

    @Test
    fun pausedTimedPlaybackShowsPausedStateAndRemainingTime() {
        val text = ReaderTtsNotificationTextResolver.resolveContentText(
            ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS,
                currentPlaybackSummary = "第三章",
                remainingTimerMillis = 90 * 60_000L,
                activeStateLabel = "朗读中",
            ),
        )

        assertEquals("第三章 · 已暂停 · 1h 30m", text)
    }

    @Test
    fun expiredTimerFallsBackToStableActiveStateLabel() {
        val text = ReaderTtsNotificationTextResolver.resolveContentText(
            ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PLAYING,
                currentPlaybackSummary = "第三章",
                remainingTimerMillis = 0L,
                activeStateLabel = "朗读中",
            ),
        )

        assertEquals("第三章 · 朗读中", text)
        assertFalse(text.contains("0m"))
    }

    @Test
    fun blankSummaryFallsBackToStateLineOnly() {
        val text = ReaderTtsNotificationTextResolver.resolveContentText(
            ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PLAYING,
                currentPlaybackSummary = "",
                remainingTimerMillis = null,
                activeStateLabel = "朗读中",
            ),
        )

        assertEquals("朗读中", text)
    }
}
