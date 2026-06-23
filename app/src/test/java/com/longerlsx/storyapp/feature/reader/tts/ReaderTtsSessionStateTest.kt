package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsSessionStateTest {

    @Test
    fun exposesAllExpectedSessionStates() {
        assertEquals(
            listOf(
                ReaderTtsSessionState.OFF,
                ReaderTtsSessionState.STARTING,
                ReaderTtsSessionState.PLAYING,
                ReaderTtsSessionState.PAUSED_BY_USER,
                ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS,
                ReaderTtsSessionState.STOPPED_BY_USER,
                ReaderTtsSessionState.STOPPED_BY_NAVIGATION,
                ReaderTtsSessionState.STOPPED_BY_TIMER,
                ReaderTtsSessionState.STOPPED_AT_BOOK_END,
                ReaderTtsSessionState.FAILED,
            ),
            ReaderTtsSessionState.entries,
        )
    }

    @Test
    fun classifiesOngoingSessionStates() {
        assertFalse(ReaderTtsSessionState.OFF.isOngoingSession())
        assertTrue(ReaderTtsSessionState.STARTING.isOngoingSession())
        assertTrue(ReaderTtsSessionState.PLAYING.isOngoingSession())
        assertTrue(ReaderTtsSessionState.PAUSED_BY_USER.isOngoingSession())
        assertTrue(ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS.isOngoingSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_USER.isOngoingSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_NAVIGATION.isOngoingSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_TIMER.isOngoingSession())
        assertFalse(ReaderTtsSessionState.STOPPED_AT_BOOK_END.isOngoingSession())
        assertFalse(ReaderTtsSessionState.FAILED.isOngoingSession())
    }

    @Test
    fun classifiesSpeakingSessionStates() {
        assertFalse(ReaderTtsSessionState.OFF.isSpeakingSession())
        assertTrue(ReaderTtsSessionState.STARTING.isSpeakingSession())
        assertTrue(ReaderTtsSessionState.PLAYING.isSpeakingSession())
        assertFalse(ReaderTtsSessionState.PAUSED_BY_USER.isSpeakingSession())
        assertFalse(ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS.isSpeakingSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_USER.isSpeakingSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_NAVIGATION.isSpeakingSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_TIMER.isSpeakingSession())
        assertFalse(ReaderTtsSessionState.STOPPED_AT_BOOK_END.isSpeakingSession())
        assertFalse(ReaderTtsSessionState.FAILED.isSpeakingSession())
    }

    @Test
    fun classifiesPausedSessionStates() {
        assertFalse(ReaderTtsSessionState.OFF.isPausedSession())
        assertFalse(ReaderTtsSessionState.STARTING.isPausedSession())
        assertFalse(ReaderTtsSessionState.PLAYING.isPausedSession())
        assertTrue(ReaderTtsSessionState.PAUSED_BY_USER.isPausedSession())
        assertTrue(ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS.isPausedSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_USER.isPausedSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_NAVIGATION.isPausedSession())
        assertFalse(ReaderTtsSessionState.STOPPED_BY_TIMER.isPausedSession())
        assertFalse(ReaderTtsSessionState.STOPPED_AT_BOOK_END.isPausedSession())
        assertFalse(ReaderTtsSessionState.FAILED.isPausedSession())
    }
}
