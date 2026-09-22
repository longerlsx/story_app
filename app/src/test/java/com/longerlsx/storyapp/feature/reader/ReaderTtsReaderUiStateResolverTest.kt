package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsReaderUiStateResolverTest {

    @Test
    fun finishedListeningExplainsWhyItStoppedWithoutClaimingAnActiveSession() {
        for ((terminal, message) in listOf(
            ReaderTtsSessionState.STOPPED_BY_TIMER to "定时结束",
            ReaderTtsSessionState.STOPPED_AT_BOOK_END to "已读完",
        )) {
            val runtime = ReaderTtsRuntimeState(playbackState = terminal, currentBookId = "book-1")
            val state = ReaderTtsReaderUiStateResolver.resolve("book-1", runtime)
            assertEquals(message, state.statusText)
            assertEquals("朗读", state.toggleState.actionLabel)
            assertFalse(state.toggleState.showImmersiveAction)
            assertEquals("未开始", ReaderTtsReaderUiStateResolver.resolve("book-2", runtime).statusText)
        }
    }

    @Test
    fun playingPrimaryActionOffersPauseInsteadOfDestroyingTheListeningSession() {
        val state = ReaderTtsReaderUiStateResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.PLAYING,
                currentBookId = "book-1",
                remainingTimerMillis = 28 * 60_000L,
            ),
        )

        assertEquals("听书", state.toggleState.actionLabel)
        assertEquals("暂停朗读", state.toggleState.immersiveActionLabel)
        assertTrue(state.toggleState.showImmersiveAction)
        assertEquals("正在朗读", state.statusText)
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

        assertEquals("听书", state.toggleState.actionLabel)
        assertTrue(state.toggleState.showImmersiveAction)
        assertEquals("已暂停", state.statusText)
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

        assertEquals("听书", state.toggleState.actionLabel)
        assertEquals("暂停朗读", state.toggleState.immersiveActionLabel)
        assertTrue(state.toggleState.showImmersiveAction)
        assertEquals("正在朗读", state.statusText)
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

        assertEquals("听书", state.toggleState.actionLabel)
        assertEquals("继续朗读", state.toggleState.immersiveActionLabel)
        assertTrue(state.toggleState.showImmersiveAction)
        assertEquals("已暂停", state.statusText)
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
        assertEquals("未开始", state.statusText)
        assertEquals(null, state.remainingTimeLabel)
    }

    @Test
    fun preparingAudioDoesNotClaimSoundIsAlreadyPlaying() {
        val state = ReaderTtsReaderUiStateResolver.resolve(
            "book-1",
            ReaderTtsRuntimeState(playbackState = ReaderTtsSessionState.STARTING, currentBookId = "book-1"),
        )

        assertEquals("正在准备离线声音…", state.statusText)
        assertEquals("听书", state.toggleState.actionLabel)
    }

    @Test
    fun failedPlaybackKeepsTheReasonAndOffersAnExplicitRetryEntry() {
        val state = ReaderTtsReaderUiStateResolver.resolve(
            "book-1",
            ReaderTtsRuntimeState(
                playbackState = ReaderTtsSessionState.FAILED,
                currentBookId = "book-1",
                localErrorMessage = "无法读取声音文件",
            ),
        )

        assertEquals("重试朗读", state.toggleState.immersiveActionLabel)
        assertEquals("朗读失败", state.statusText)
    }
}
