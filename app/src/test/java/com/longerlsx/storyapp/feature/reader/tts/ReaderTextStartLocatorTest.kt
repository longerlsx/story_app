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
    fun resolvesScrollTopLocationFromVisibleBodyInsteadOfWholeItemGeometry() {
        val location = ReaderTextStartLocator.resolveScrollTopLocation(
            visibleItems = listOf(
                ReaderVisibleChapterItem(
                    itemIndex = 0,
                    chapterIndex = 1,
                    offsetPx = -120,
                    sizePx = 560,
                    bodyOffsetPx = 28,
                    bodyHeightPx = 180,
                ),
                ReaderVisibleChapterItem(
                    itemIndex = 1,
                    chapterIndex = 2,
                    offsetPx = 320,
                    sizePx = 300,
                    bodyOffsetPx = 360,
                    bodyHeightPx = 220,
                ),
            ),
            chapterTextByIndex = mapOf(
                1 to "a".repeat(400),
                2 to "b".repeat(320),
            ),
            viewportTopPx = 0,
        )

        assertEquals(ReaderTextStartLocation(chapterIndex = 1, charOffset = 0), location)
    }

    @Test
    fun resolvesScrollTopLocationFromReadableViewportTopInset() {
        val location = ReaderTextStartLocator.resolveScrollTopLocation(
            visibleItems = listOf(
                ReaderVisibleChapterItem(
                    itemIndex = 0,
                    chapterIndex = 1,
                    offsetPx = -120,
                    sizePx = 560,
                    bodyOffsetPx = 28,
                    bodyHeightPx = 180,
                ),
            ),
            chapterTextByIndex = mapOf(
                1 to "a".repeat(400),
            ),
            viewportTopPx = 52,
        )

        assertEquals(ReaderTextStartLocation(chapterIndex = 1, charOffset = 53), location)
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
    fun resolvesPageTopLocationFromVisiblePageStartOffset() {
        val location = ReaderTextStartLocator.resolvePageTopLocation(
            chapterIndex = 4,
            pages = listOf(
                ReaderPageSlice(
                    startCharOffset = 0,
                    endCharOffset = 120,
                    text = "正文",
                    visibleStartCharOffset = 6,
                    visibleEndCharOffset = 8,
                ),
                ReaderPageSlice(
                    startCharOffset = 120,
                    endCharOffset = 260,
                    text = "下一页正文",
                    visibleStartCharOffset = 123,
                    visibleEndCharOffset = 128,
                ),
            ),
            currentPageIndex = 0,
        )

        assertEquals(ReaderTextStartLocation(chapterIndex = 4, charOffset = 6), location)
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

    @Test
    fun restartVisualRangeUsesShortForwardPreviewFromResolvedLocation() {
        val range = ReaderTextStartLocation(
            chapterIndex = 3,
            charOffset = 2,
        ).restartVisualRangeOrNull("0123456789abcdef")

        assertEquals(
            ReaderTtsActiveVisualRange(
                chapterIndex = 3,
                startCharOffset = 2,
                endCharOffset = 10,
            ),
            range,
        )
    }

    @Test
    fun restartVisualRangeClampsLocationInsideChapterText() {
        val range = ReaderTextStartLocation(
            chapterIndex = 5,
            charOffset = 99,
        ).restartVisualRangeOrNull("正文")

        assertEquals(
            ReaderTtsActiveVisualRange(
                chapterIndex = 5,
                startCharOffset = 1,
                endCharOffset = 2,
            ),
            range,
        )
    }

    @Test
    fun restartVisualRangeReturnsNullForEmptyChapterText() {
        val range = ReaderTextStartLocation(
            chapterIndex = 1,
            charOffset = 0,
        ).restartVisualRangeOrNull("")

        assertNull(range)
    }
}
