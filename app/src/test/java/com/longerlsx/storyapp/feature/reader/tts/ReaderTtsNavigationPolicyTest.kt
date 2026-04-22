package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsNavigationPolicyTest {

    @Test
    fun stopsWhenOpeningDifferentBookDuringActiveSession() {
        assertTrue(
            ReaderTtsNavigationPolicy.shouldStopForOpenReader(
                playbackState = ReaderTtsSessionState.PLAYING,
                activeBookId = "book-a",
                nextBookId = "book-b",
            ),
        )
    }

    @Test
    fun doesNotStopWhenOpeningSameBook() {
        assertFalse(
            ReaderTtsNavigationPolicy.shouldStopForOpenReader(
                playbackState = ReaderTtsSessionState.PLAYING,
                activeBookId = "book-a",
                nextBookId = "book-a",
            ),
        )
    }

    @Test
    fun doesNotStopWhenThereIsNoActiveSession() {
        assertFalse(
            ReaderTtsNavigationPolicy.shouldStopForOpenReader(
                playbackState = ReaderTtsSessionState.STOPPED_BY_USER,
                activeBookId = "book-a",
                nextBookId = "book-b",
            ),
        )
    }
}
