package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsReaderUiStateResolverTest {

    @Test
    fun showsStopLabelForCurrentBookTimedPlayback() {
        val state = ReaderTtsReaderUiStateResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PLAYING,
                currentBookId = "book-1",
                remainingTimerMillis = 28 * 60_000L,
            ),
        )

        assertEquals("停止朗读 · 28m", state.toggleState.actionLabel)
        assertEquals("停止朗读 · 28m", state.toggleState.immersiveActionLabel)
        assertTrue(state.toggleState.showImmersiveAction)
        assertEquals("当前状态：朗读中", state.statusText)
        assertEquals("28m", state.remainingTimeLabel)
    }

    @Test
    fun showsResumeLabelForCurrentBookPausedTimedPlayback() {
        val state = ReaderTtsReaderUiStateResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PAUSED_BY_USER,
                currentBookId = "book-1",
                remainingTimerMillis = 90 * 60_000L,
            ),
        )

        assertEquals("继续朗读 · 1h 30m", state.toggleState.actionLabel)
        assertTrue(state.toggleState.showImmersiveAction)
        assertEquals("当前状态：已暂停", state.statusText)
        assertEquals("1h 30m", state.remainingTimeLabel)
    }

    @Test
    fun suppressesExpiredTimerLabelForCurrentBookPlayback() {
        val state = ReaderTtsReaderUiStateResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PLAYING,
                currentBookId = "book-1",
                remainingTimerMillis = 0L,
            ),
        )

        assertEquals("停止朗读", state.toggleState.actionLabel)
        assertEquals("停止朗读", state.toggleState.immersiveActionLabel)
        assertTrue(state.toggleState.showImmersiveAction)
        assertEquals("当前状态：朗读中", state.statusText)
        assertEquals(null, state.remainingTimeLabel)
    }

    @Test
    fun suppressesNegativeTimerLabelForCurrentBookPausedPlayback() {
        val state = ReaderTtsReaderUiStateResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PAUSED_BY_USER,
                currentBookId = "book-1",
                remainingTimerMillis = -1L,
            ),
        )

        assertEquals("继续朗读", state.toggleState.actionLabel)
        assertEquals("继续朗读", state.toggleState.immersiveActionLabel)
        assertTrue(state.toggleState.showImmersiveAction)
        assertEquals("当前状态：已暂停", state.statusText)
        assertEquals(null, state.remainingTimeLabel)
    }

    @Test
    fun ignoresPlaybackFromAnotherBook() {
        val state = ReaderTtsReaderUiStateResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PLAYING,
                currentBookId = "book-2",
                remainingTimerMillis = 28 * 60_000L,
            ),
        )

        assertEquals("朗读", state.toggleState.actionLabel)
        assertFalse(state.toggleState.showImmersiveAction)
        assertEquals("当前状态：未朗读", state.statusText)
        assertEquals(null, state.remainingTimeLabel)
    }
}
