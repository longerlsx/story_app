package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.graphics.Color
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHighlightAnnotatorTest {

    @Test
    fun highlightsIntersectionOfAbsoluteRangeAndVisibleText() {
        val annotated = ReaderHighlightAnnotator.annotate(
            text = "可见正文",
            textStartCharOffset = 10,
            textEndCharOffset = 14,
            highlightRange = ReaderTtsCharacterRange(
                startCharOffset = 11,
                endCharOffset = 13,
            ),
            highlightColor = Color.Yellow,
        )

        assertEquals("可见正文", annotated.text)
        assertEquals(1, annotated.spanStyles.size)
        assertEquals(1, annotated.spanStyles.single().start)
        assertEquals(3, annotated.spanStyles.single().end)
        assertEquals(Color.Yellow, annotated.spanStyles.single().item.background)
    }

    @Test
    fun clampsHighlightWhenRangeStartsBeforeVisibleText() {
        val annotated = ReaderHighlightAnnotator.annotate(
            text = "正文",
            textStartCharOffset = 20,
            textEndCharOffset = 22,
            highlightRange = ReaderTtsCharacterRange(
                startCharOffset = 18,
                endCharOffset = 21,
            ),
            highlightColor = Color.Cyan,
        )

        assertEquals(0, annotated.spanStyles.single().start)
        assertEquals(1, annotated.spanStyles.single().end)
    }

    @Test
    fun returnsPlainTextWhenHighlightDoesNotIntersectVisibleText() {
        val annotated = ReaderHighlightAnnotator.annotate(
            text = "正文",
            textStartCharOffset = 20,
            textEndCharOffset = 22,
            highlightRange = ReaderTtsCharacterRange(
                startCharOffset = 22,
                endCharOffset = 24,
            ),
            highlightColor = Color.Magenta,
        )

        assertEquals("正文", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun returnsPlainTextWhenVisibleAbsoluteRangeIsCollapsed() {
        val annotated = ReaderHighlightAnnotator.annotate(
            text = "当前章节暂无正文。",
            textStartCharOffset = 0,
            textEndCharOffset = 0,
            highlightRange = ReaderTtsCharacterRange(
                startCharOffset = 0,
                endCharOffset = 2,
            ),
            highlightColor = Color.Green,
        )

        assertTrue(annotated.spanStyles.isEmpty())
    }
}
