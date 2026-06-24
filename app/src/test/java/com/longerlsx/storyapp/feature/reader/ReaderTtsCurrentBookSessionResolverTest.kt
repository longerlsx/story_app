package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsCurrentBookSessionResolverTest {

    @Test
    fun marksCurrentBookPlayingSessionAsSpeakingAndOngoing() {
        val state = ReaderTtsCurrentBookSessionResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "book-1",
                playbackState = ReaderTtsSessionState.PLAYING,
            ),
        )

        assertTrue(state.isSpeaking)
        assertFalse(state.isPaused)
        assertTrue(state.isOngoing)
    }

    @Test
    fun marksCurrentBookPausedSessionAsPausedAndOngoing() {
        val state = ReaderTtsCurrentBookSessionResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "book-1",
                playbackState = ReaderTtsSessionState.PAUSED_BY_USER,
            ),
        )

        assertFalse(state.isSpeaking)
        assertTrue(state.isPaused)
        assertTrue(state.isOngoing)
    }

    @Test
    fun suppressesOtherBookPlaybackState() {
        val state = ReaderTtsCurrentBookSessionResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "book-2",
                playbackState = ReaderTtsSessionState.PLAYING,
            ),
        )

        assertFalse(state.isSpeaking)
        assertFalse(state.isPaused)
        assertFalse(state.isOngoing)
    }

    @Test
    fun treatsStoppedCurrentBookAsNotOngoing() {
        val state = ReaderTtsCurrentBookSessionResolver.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "book-1",
                playbackState = ReaderTtsSessionState.STOPPED_BY_USER,
            ),
        )

        assertFalse(state.isSpeaking)
        assertFalse(state.isPaused)
        assertFalse(state.isOngoing)
    }
}
