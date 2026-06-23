package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderParagraphModelTest {

    @Test
    fun trimsDisplayParagraphsButKeepsRawChapterOffsets() {
        val text = "\n  第一段正文  \n\n\t第二段正文\t\n"

        val paragraphs = ReaderParagraphModel.toDisplayParagraphs(text)

        assertEquals(
            listOf(
                ReaderDisplayParagraph(
                    text = "第一段正文",
                    startCharOffset = text.indexOf('第'),
                    endCharOffset = text.indexOf("  \n"),
                ),
                ReaderDisplayParagraph(
                    text = "第二段正文",
                    startCharOffset = text.indexOf('第', startIndex = text.indexOf("第二")),
                    endCharOffset = text.indexOf('\t', startIndex = text.indexOf("第二")),
                ),
            ),
            paragraphs,
        )
    }

    @Test
    fun blankContentReturnsReaderPlaceholderWithoutRawBodyRange() {
        assertEquals(
            listOf(
                ReaderDisplayParagraph(
                    text = "当前章节暂无正文。",
                    startCharOffset = 0,
                    endCharOffset = 0,
                ),
            ),
            ReaderParagraphModel.toDisplayParagraphs(" \n\t\n"),
        )
    }
}
