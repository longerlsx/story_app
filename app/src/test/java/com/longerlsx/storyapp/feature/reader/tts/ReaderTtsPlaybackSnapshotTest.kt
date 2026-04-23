package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTtsPlaybackSnapshotTest {

    @Test
    fun exposesPlaybackIdentityAndRecoveryFields() {
        val segment = ReaderTtsSegment(
            chapterIndex = 2,
            startCharOffset = 10,
            endCharOffset = 24,
            spokenText = "Hello world.",
        )
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = segment,
            lastConfirmedSpokenRange = ReaderTtsCharacterRange(10, 18),
            nextRecoverableCharOffset = 19,
            nextRecoverableRange = ReaderTtsCharacterRange(19, 24),
            activePauseReason = ReaderTtsPauseReason.AUDIO_FOCUS,
        )

        assertEquals(segment, snapshot.currentSegment)
        assertEquals(ReaderTtsCharacterRange(10, 18), snapshot.lastConfirmedSpokenRange)
        assertEquals(19, snapshot.nextRecoverableCharOffset)
        assertEquals(ReaderTtsCharacterRange(19, 24), snapshot.nextRecoverableRange)
        assertEquals(ReaderTtsPauseReason.AUDIO_FOCUS, snapshot.activePauseReason)
    }

    @Test
    fun defaultsToEmptyPlaybackState() {
        val snapshot = ReaderTtsPlaybackSnapshot()

        assertNull(snapshot.currentSegment)
        assertNull(snapshot.lastConfirmedSpokenRange)
        assertNull(snapshot.nextRecoverableCharOffset)
        assertNull(snapshot.nextRecoverableRange)
        assertNull(snapshot.activePauseReason)
    }

    @Test
    fun prefersConfirmedTimingRangeForActiveVisualRange() {
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = ReaderTtsSegment(
                chapterIndex = 4,
                startCharOffset = 10,
                endCharOffset = 28,
                spokenText = "这是一个短句。",
            ),
            lastConfirmedSpokenRange = ReaderTtsCharacterRange(15, 21),
        )

        assertEquals(
            ReaderTtsActiveVisualRange(
                chapterIndex = 4,
                startCharOffset = 15,
                endCharOffset = 21,
            ),
            snapshot.activeVisualRangeOrNull(),
        )
    }

    @Test
    fun fallsBackToCurrentSegmentRangeWhenTimingIsUnavailable() {
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = ReaderTtsSegment(
                chapterIndex = 6,
                startCharOffset = 40,
                endCharOffset = 58,
                spokenText = "系统还没给 timing。",
            ),
        )

        assertEquals(
            ReaderTtsActiveVisualRange(
                chapterIndex = 6,
                startCharOffset = 40,
                endCharOffset = 58,
            ),
            snapshot.activeVisualRangeOrNull(),
        )
    }

    @Test
    fun fallsBackToCurrentSegmentRangeWhenConfirmedTimingRangeIsCollapsed() {
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = ReaderTtsSegment(
                chapterIndex = 8,
                startCharOffset = 100,
                endCharOffset = 124,
                spokenText = "朗读已经走到这句结尾。",
            ),
            lastConfirmedSpokenRange = ReaderTtsCharacterRange(124, 124),
        )

        assertEquals(
            ReaderTtsActiveVisualRange(
                chapterIndex = 8,
                startCharOffset = 100,
                endCharOffset = 124,
            ),
            snapshot.activeVisualRangeOrNull(),
        )
    }

    @Test
    fun prefersRecoverableOffsetForActiveFollowPositionWhenTimingRangeIsCollapsed() {
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = ReaderTtsSegment(
                chapterIndex = 8,
                startCharOffset = 100,
                endCharOffset = 124,
                spokenText = "朗读已经走到这句结尾。",
            ),
            lastConfirmedSpokenRange = ReaderTtsCharacterRange(124, 124),
            nextRecoverableCharOffset = 124,
            nextRecoverableRange = ReaderTtsCharacterRange(124, 124),
        )

        assertEquals(124, snapshot.activeFollowCharOffsetOrNull())
    }

    @Test
    fun fallsBackToCurrentSegmentStartForActiveFollowPositionWhenNoProgressExists() {
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = ReaderTtsSegment(
                chapterIndex = 6,
                startCharOffset = 40,
                endCharOffset = 58,
                spokenText = "系统还没给 timing。",
            ),
        )

        assertEquals(40, snapshot.activeFollowCharOffsetOrNull())
    }
}
