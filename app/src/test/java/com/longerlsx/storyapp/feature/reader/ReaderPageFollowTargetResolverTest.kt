package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsPlaybackSnapshot
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageFollowTargetResolverTest {

    @Test
    fun usesRecoverableOffsetWhenTimingRangeCollapsesAtSegmentEnd() {
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = ReaderTtsSegment(
                chapterIndex = 2,
                startCharOffset = 120,
                endCharOffset = 240,
                spokenText = "segment",
            ),
            lastConfirmedSpokenRange = ReaderTtsCharacterRange(
                startCharOffset = 240,
                endCharOffset = 240,
            ),
            nextRecoverableCharOffset = 240,
            nextRecoverableRange = ReaderTtsCharacterRange(
                startCharOffset = 240,
                endCharOffset = 240,
            ),
        )

        assertEquals(
            240,
            resolvePageFollowTargetCharOffset(
                playbackSnapshot = snapshot,
                selectedChapterIndex = 2,
                isFollowSuppressed = false,
            ),
        )
    }

    @Test
    fun returnsNullWhenFollowIsSuppressed() {
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = ReaderTtsSegment(
                chapterIndex = 0,
                startCharOffset = 12,
                endCharOffset = 24,
                spokenText = "segment",
            ),
        )

        assertNull(
            resolvePageFollowTargetCharOffset(
                playbackSnapshot = snapshot,
                selectedChapterIndex = 0,
                isFollowSuppressed = true,
            ),
        )
    }

    @Test
    fun returnsNullWhenSnapshotChapterDoesNotMatchSelectedChapter() {
        val snapshot = ReaderTtsPlaybackSnapshot(
            currentSegment = ReaderTtsSegment(
                chapterIndex = 1,
                startCharOffset = 12,
                endCharOffset = 24,
                spokenText = "segment",
            ),
        )

        assertNull(
            resolvePageFollowTargetCharOffset(
                playbackSnapshot = snapshot,
                selectedChapterIndex = 0,
                isFollowSuppressed = false,
            ),
        )
    }
}
