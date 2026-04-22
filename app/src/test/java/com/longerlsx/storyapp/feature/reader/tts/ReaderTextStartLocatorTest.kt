package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.feature.reader.ReaderPageSlice
import com.longerlsx.storyapp.feature.reader.ReaderVisibleChapterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTextStartLocatorTest {

    @Test
    fun resolvesScrollTopLocationFromFirstVisibleChapterCrossingViewportTop() {
        val location = ReaderTextStartLocator.resolveScrollTopLocation(
            visibleItems = listOf(
                ReaderVisibleChapterItem(itemIndex = 0, chapterIndex = 1, offsetPx = -120, sizePx = 400),
                ReaderVisibleChapterItem(itemIndex = 1, chapterIndex = 2, offsetPx = 280, sizePx = 320),
            ),
            chapterTextByIndex = mapOf(
                1 to "a".repeat(1000),
                2 to "b".repeat(320),
            ),
            viewportTopPx = 0,
        )

        assertEquals(ReaderTextStartLocation(chapterIndex = 1, charOffset = 300), location)
    }

    @Test
    fun resolvesPageTopLocationFromCurrentPageStartOffset() {
        val location = ReaderTextStartLocator.resolvePageTopLocation(
            chapterIndex = 7,
            pages = listOf(
                ReaderPageSlice(startCharOffset = 0, endCharOffset = 120),
                ReaderPageSlice(startCharOffset = 120, endCharOffset = 260),
                ReaderPageSlice(startCharOffset = 260, endCharOffset = 400),
            ),
            currentPageIndex = 1,
        )

        assertEquals(ReaderTextStartLocation(chapterIndex = 7, charOffset = 120), location)
    }

    @Test
    fun absorbsForwardOverWhitespaceAndPunctuationUntilReadableContent() {
        val text = " \n……，？！正文开始"

        val location = ReaderTextStartLocator.resolveRestartLocation(
            chapterIndex = 3,
            text = text,
            pressedCharOffset = 0,
            nonBodyRanges = emptyList(),
        )

        assertEquals(
            ReaderTextStartLocation(
                chapterIndex = 3,
                charOffset = text.indexOf('正'),
            ),
            location,
        )
    }

    @Test
    fun absorbsForwardAcrossNonBodyRanges() {
        val text = "标题\n正文开始"

        val location = ReaderTextStartLocator.resolveRestartLocation(
            chapterIndex = 5,
            text = text,
            pressedCharOffset = 0,
            nonBodyRanges = listOf(0..1),
        )

        assertEquals(
            ReaderTextStartLocation(
                chapterIndex = 5,
                charOffset = text.indexOf('正'),
            ),
            location,
        )
    }

    @Test
    fun returnsNullWhenNoReadableTextExistsAfterPressLocation() {
        val location = ReaderTextStartLocator.resolveRestartLocation(
            chapterIndex = 9,
            text = "   ……？！",
            pressedCharOffset = 0,
            nonBodyRanges = emptyList(),
        )

        assertNull(location)
    }
}
