package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsSegmenterTest {

    @Test
    fun splitsTextIntoSentenceChunksWithoutBreakingWords() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 4,
            text = "Hello world. This is a test. Keep going.",
            maxChunkChars = 30,
        )

        assertEquals(2, segments.size)
        assertEquals("Hello world. This is a test.", segments[0].spokenText)
        assertEquals("Keep going.", segments[1].spokenText)
    }

    @Test
    fun preservesChapterIndexAndCharacterOffsets() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 7,
            text = "Alpha. Beta.",
            maxChunkChars = 6,
        )

        assertEquals(
            listOf(
                ReaderTtsSegment(
                    chapterIndex = 7,
                    startCharOffset = 0,
                    endCharOffset = 6,
                    spokenText = "Alpha.",
                ),
                ReaderTtsSegment(
                    chapterIndex = 7,
                    startCharOffset = 7,
                    endCharOffset = 12,
                    spokenText = "Beta.",
                ),
            ),
            segments,
        )
    }

    @Test
    fun keepsLongSingleSentenceAsOneSegmentInsteadOfSplittingWords() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 9,
            text = "A very long sentence that should stay intact.",
            maxChunkChars = 10,
        )

        assertEquals(1, segments.size)
        assertEquals("A very long sentence that should stay intact.", segments[0].spokenText)
    }
}
