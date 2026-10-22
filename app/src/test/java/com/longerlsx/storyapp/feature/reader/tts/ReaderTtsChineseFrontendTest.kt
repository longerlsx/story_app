package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsChineseFrontendTest {
    private fun frontend(characters: String) = ReaderTtsChineseFrontend(characters.codePoints().toArray().toSet())
    private fun segment(text: String) = ReaderTtsSegment(2, 17, 17 + text.length, text)

    @Test
    fun formalSegmentationPreparesARealChapterTailWithAStandaloneMiddleDot() {
        val text = "砰砰砰！\n大脑存放处.jpg[可怜]\n·"
        val source = ReaderTtsSegmenter.segment(1, text, maxChunkChars = 40).single()
        assertEquals("砰砰砰！ 大脑存放处.j p g 可怜",
            frontend("砰！大脑存放处.jpg可怜").prepare(source))
        assertEquals(text.length, source.endCharOffset)
    }

    @Test
    fun asciiWordsAreLowercasedAndSeparatedForNativeSingleLetterLookup() {
        val characters = "abcdefghijklmnopqrstuvwxyz"
        assertEquals(characters.toList().joinToString(" "), frontend(characters).prepare(segment(characters.uppercase())))
        assertEquals("使用a i和c p u。", frontend("使用和。aicpu").prepare(segment("使用ai和CPU。")))
    }

    @Test
    fun quotesBracketsAndWhitespaceAreExplicitlyNormalizedWithoutRemovingWords() {
        assertEquals("他说： a i 可以。", frontend("他说：可以。ai").prepare(segment("他说：“AI”\n（可以）。")))
    }

    @Test
    fun unknownReadableCharacterRejectsTheWholeUnitInsteadOfPlayingItsKnownPrefix() {
        val failure = assertThrows(IllegalStateException::class.java) {
            frontend("山。风").prepare(segment("山𰻞风。"))
        }
        assertTrue(failure.message.orEmpty().contains("𰻞"))
        assertTrue(failure.message.orEmpty().contains("30EDE"))
    }

    @Test
    fun codePointCoverageSupportsSupplementaryCharactersAndNeverChangesSourceCoordinates() {
        val source = segment("山𠀀风。")
        assertEquals("山𠀀风。", frontend("山𠀀风。").prepare(source))
        assertEquals(ReaderTtsCharacterRange(18, 20), source.sourceRangeForSpokenRange(1, 3))
        assertEquals("山𠀀风。", source.spokenText)
    }

    @Test
    fun numberExpansionMustAlsoPassCoverageBeforeAnyAudioIsGenerated() {
        assertEquals("温度负五度。", frontend("温度负五。").prepare(segment("温度-5度。")))
        assertThrows(IllegalStateException::class.java) {
            frontend("温度五。").prepare(segment("温度-5度。"))
        }
    }
}
