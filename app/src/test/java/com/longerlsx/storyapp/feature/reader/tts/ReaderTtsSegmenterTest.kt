package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        assertEquals(listOf(7, 7), segments.map { it.chapterIndex })
        assertEquals(listOf(0 to 6, 7 to 12), segments.map { it.startCharOffset to it.endCharOffset })
        assertEquals(listOf("Alpha.", "Beta."), segments.map { it.spokenText })
    }

    @Test
    fun splitsAnUnpunctuatedLongSentenceIntoBoundedChunksWithoutLosingText() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 9,
            text = "天地玄黄宇宙洪荒日月盈昃辰宿列张",
            maxChunkChars = 6,
        )

        assertEquals(listOf("天地玄黄宇宙", "洪荒日月盈昃", "辰宿列张"), segments.map { it.spokenText })
        assertEquals(listOf(0 to 6, 6 to 12, 12 to 16), segments.map { it.startCharOffset to it.endCharOffset })
    }

    @Test
    fun defaultChunksStayShortEvenWhenTheChapterHasNoSentenceTerminators() {
        val text = "正文".repeat(101)
        val segments = ReaderTtsSegmenter.segment(chapterIndex = 1, text = text)

        assertTrue(segments.all { it.spokenText.codePointCount(0, it.spokenText.length) <= 80 })
        assertEquals(text, segments.joinToString("") { it.spokenText })
    }

    @Test
    fun aOneCharacterLimitKeepsSupplementaryCharactersWholeAndSourceOffsetsInUtf16() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 2,
            text = "甲😀乙𠀀丙",
            maxChunkChars = 1,
        )

        assertEquals(listOf("甲", "😀", "乙", "𠀀", "丙"), segments.map { it.spokenText })
        assertEquals(listOf(0 to 1, 1 to 3, 3 to 4, 4 to 6, 6 to 7), segments.map { it.startCharOffset to it.endCharOffset })
    }

    @Test
    fun decimalNumbersAreNeitherSplitAtThePointNorRewrittenWithAnInsertedSpace() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 0,
            text = "价格12.5元，重量3.14千克。",
            maxChunkChars = 10,
        )

        assertEquals(listOf("价格12.5元，", "重量3.14千克。"), segments.map { it.spokenText })
    }

    @Test
    fun anOrdinaryNumberMovesTogetherToTheNextChunkWhenItFits() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 0,
            text = "共有12345件物品",
            maxChunkChars = 6,
        )

        assertEquals(listOf("共有", "12345件", "物品"), segments.map { it.spokenText })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnImpossibleChunkLimitInsteadOfLoopingOrProducingUnboundedChunks() {
        ReaderTtsSegmenter.segment(chapterIndex = 0, text = "正文", maxChunkChars = 0)
    }

    @Test
    fun removesNoisySymbolsFromSpokenTextWithoutChangingOffsets() {
        val text = "【标题】 - 第一段*内容。"
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 3,
            text = text,
            maxChunkChars = 80,
        )

        assertEquals(1, segments.size)
        assertEquals("标题 第一段 内容。", segments.single().spokenText)
        assertEquals(0, segments.single().startCharOffset)
        assertEquals(text.length, segments.single().endCharOffset)
    }

    @Test
    fun callbackRangesMapToSourceAfterCollapsedWhitespaceAndRemovedSymbols() {
        val segment = ReaderTtsSegmenter.segment(
            chapterIndex = 3,
            text = "【你好】 \t世界。\n 下一句！",
        ).single()

        assertEquals("你好 世界。 下一句！", segment.spokenText)
        assertEquals(ReaderTtsCharacterRange(1, 3), segment.sourceRangeForSpokenRange(0, 2))
        assertEquals(ReaderTtsCharacterRange(3, 6), segment.sourceRangeForSpokenRange(2, 3))
        assertEquals(ReaderTtsCharacterRange(6, 8), segment.sourceRangeForSpokenRange(3, 5))
        assertEquals(ReaderTtsCharacterRange(11, 15), segment.sourceRangeForSpokenRange(7, 11))
    }

    @Test
    fun callbackEndingInsideASurrogatePairStillMapsTheWholeSourceCharacter() {
        val segment = ReaderTtsSegmenter.segment(chapterIndex = 3, text = "【😀】乙").single()

        assertEquals("😀 乙", segment.spokenText)
        assertEquals(ReaderTtsCharacterRange(1, 3), segment.sourceRangeForSpokenRange(0, 1))
        assertEquals(ReaderTtsCharacterRange(1, 3), segment.sourceRangeForSpokenRange(1, 2))
        assertEquals(ReaderTtsCharacterRange(4, 5), segment.sourceRangeForSpokenRange(3, 4))
    }

    @Test
    fun manuallyConstructedSegmentsKeepLegacyOffsetMappingAndClampCallbacks() {
        val segment = ReaderTtsSegment(2, 10, 14, "甲乙丙丁")

        assertEquals(ReaderTtsCharacterRange(11, 13), segment.sourceRangeForSpokenRange(1, 3))
        assertEquals(ReaderTtsCharacterRange(10, 14), segment.sourceRangeForSpokenRange(-10, 100))
    }

    @Test
    fun emptyAndDecorationOnlyTextDoesNotCreateSilentPlaybackSegments() {
        assertEquals(emptyList<ReaderTtsSegment>(), ReaderTtsSegmenter.segment(0, " \n\t "))
        assertEquals(emptyList<ReaderTtsSegment>(), ReaderTtsSegmenter.segment(0, "【】 -- **"))
    }

    @Test
    fun restartingInsideAChapterPreservesAbsoluteOffsetsThroughSpeechNormalization() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 4,
            text = "前文。新的【段落】  \t正文。",
            startCharOffset = 4,
        )

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals("的 段落 正文。", segment.spokenText)
        assertEquals(4, segment.startCharOffset)
        assertEquals(15, segment.endCharOffset)
        assertEquals(ReaderTtsCharacterRange(6, 8), segment.sourceRangeForSpokenRange(2, 4))
        assertEquals(ReaderTtsCharacterRange(12, 14), segment.sourceRangeForSpokenRange(5, 7))
    }

    @Test
    fun restartingAtTheLowSurrogateIncludesTheWholeCharacter() {
        val segments = ReaderTtsSegmenter.segment(
            chapterIndex = 4,
            text = "前😀正文。",
            startCharOffset = 2,
        )

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals("😀正文。", segment.spokenText)
        assertEquals(1, segment.startCharOffset)
        assertEquals(ReaderTtsCharacterRange(1, 3), segment.sourceRangeForSpokenRange(0, 2))
    }

    @Test
    fun restartingAtOrBeyondChapterEndDoesNotRepeatTheChapter() {
        assertEquals(emptyList<ReaderTtsSegment>(), ReaderTtsSegmenter.segment(0, "正文。", startCharOffset = 3))
        assertEquals(emptyList<ReaderTtsSegment>(), ReaderTtsSegmenter.segment(0, "正文。", startCharOffset = 100))
    }
}
