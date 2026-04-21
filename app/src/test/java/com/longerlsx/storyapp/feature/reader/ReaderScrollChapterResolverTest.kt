package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScrollChapterResolverTest {

    @Test
    fun resolvesChapterWhoseContentOwnsTopVisibleArea() {
        val activeChapter = ReaderScrollChapterResolver.resolveActiveChapter(
            visibleItems = listOf(
                ReaderVisibleChapterItem(itemIndex = 0, chapterIndex = 1, offsetPx = -120, sizePx = 300),
                ReaderVisibleChapterItem(itemIndex = 1, chapterIndex = 2, offsetPx = 180, sizePx = 320),
            ),
            viewportTopPx = 0,
        )

        assertEquals(1, activeChapter)
    }

    @Test
    fun skipsItemsThatAreAlreadyCompletelyAboveViewport() {
        val activeChapter = ReaderScrollChapterResolver.resolveActiveChapter(
            visibleItems = listOf(
                ReaderVisibleChapterItem(itemIndex = 0, chapterIndex = 4, offsetPx = -400, sizePx = 180),
                ReaderVisibleChapterItem(itemIndex = 1, chapterIndex = 5, offsetPx = -40, sizePx = 260),
            ),
            viewportTopPx = 0,
        )

        assertEquals(5, activeChapter)
    }
}
