package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ListeningProgress
import com.longerlsx.storyapp.feature.reader.tts.ReaderTextStartLocation
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsPlaybackSnapshot
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegment
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTtsResumeLocationResolverTest {
    @Test
    fun aColdSessionResumesThePersistedListeningPosition() {
        assertEquals(
            ReaderTextStartLocation(3, 42),
            ReaderTtsResumeLocationResolver.resolve("book", ReaderTtsRuntimeState(), saved()),
        )
    }

    @Test
    fun theCurrentPausedSessionTakesPrecedenceOverAnOlderDiskPosition() {
        val runtime = ReaderTtsRuntimeState(
            currentBookId = "book",
            playbackState = ReaderTtsSessionState.PAUSED_BY_USER,
            playbackSnapshot = ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(4, 120, 150, "待继续的正文"),
                nextRecoverableCharOffset = 120,
            ),
        )
        assertEquals(ReaderTextStartLocation(4, 120), ReaderTtsResumeLocationResolver.resolve("book", runtime, saved()))
    }

    @Test
    fun anotherBookAndACompletedCheckpointDoNotBecomeThisBooksResumeTarget() {
        assertNull(ReaderTtsResumeLocationResolver.resolve("other", ReaderTtsRuntimeState(), saved()))
        assertNull(ReaderTtsResumeLocationResolver.resolve("book", ReaderTtsRuntimeState(), saved().copy(completed = true)))
    }

    @Test
    fun aFailedSessionKeepsItsLastRecoverableSentence() {
        val runtime = ReaderTtsRuntimeState(
            currentBookId = "book",
            playbackState = ReaderTtsSessionState.FAILED,
            playbackSnapshot = ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(2, 18, 33, "中断的句子"),
            ),
        )
        assertEquals(ReaderTextStartLocation(2, 18), ReaderTtsResumeLocationResolver.resolve("book", runtime, null))
    }

    private fun saved() = ListeningProgress("book", "书名", 3, 42, "第四章", 100)
}
