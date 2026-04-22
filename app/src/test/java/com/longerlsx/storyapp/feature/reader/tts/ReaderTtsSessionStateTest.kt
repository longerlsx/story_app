package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
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
}
