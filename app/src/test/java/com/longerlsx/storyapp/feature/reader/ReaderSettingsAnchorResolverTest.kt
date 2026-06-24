package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsAnchorResolverTest {

    @Test
    fun pageModeUsesSelectedChapterAndPageAnchorWhenScrollItemsAreStale() {
        val result = ReaderSettingsAnchorResolver.resolve(
            readingMode = ReadingMode.PAGE,
            selectedChapterIndex = 2,
            pendingRestoreCharOffset = 9,
            currentPageIndex = 1,
            currentPages = listOf(
                ReaderPageSlice(startCharOffset = 0, endCharOffset = 120),
                ReaderPageSlice(startCharOffset = 120, endCharOffset = 240),
            ),
            visibleItems = listOf(
                ReaderVisibleChapterItem(
                    itemIndex = 0,
                    chapterIndex = 0,
                    offsetPx = 0,
                    sizePx = 300,
                    bodyOffsetPx = 0,
                    bodyHeightPx = 300,
                ),
            ),
            chapters = chapters(),
            chapterTextLengthByIndex = mapOf(0 to 300, 2 to 240),
            viewportTopPx = 0,
        )

        assertEquals(ReaderSettingsAnchor(chapterIndex = 2, charOffset = 120), result)
    }

    @Test
    fun scrollModeUsesVisibleBodyGeometry() {
        val result = ReaderSettingsAnchorResolver.resolve(
            readingMode = ReadingMode.SCROLL,
            selectedChapterIndex = 2,
            pendingRestoreCharOffset = 9,
            currentPageIndex = 1,
            currentPages = emptyList(),
            visibleItems = listOf(
                ReaderVisibleChapterItem(
                    itemIndex = 0,
                    chapterIndex = 1,
                    offsetPx = -80,
                    sizePx = 360,
                    bodyOffsetPx = 40,
                    bodyHeightPx = 200,
                ),
            ),
            chapters = chapters(),
            chapterTextLengthByIndex = mapOf(1 to 400),
            viewportTopPx = 100,
        )

        assertEquals(ReaderSettingsAnchor(chapterIndex = 1, charOffset = 120), result)
    }

    @Test
    fun scrollModeFallsBackToCurrentSelectionWhenVisibleBodyIsUnavailable() {
        val result = ReaderSettingsAnchorResolver.resolve(
            readingMode = ReadingMode.SCROLL,
            selectedChapterIndex = 2,
            pendingRestoreCharOffset = 48,
            currentPageIndex = 0,
            currentPages = emptyList(),
            visibleItems = emptyList(),
            chapters = chapters(),
            chapterTextLengthByIndex = emptyMap(),
            viewportTopPx = 0,
        )

        assertEquals(ReaderSettingsAnchor(chapterIndex = 2, charOffset = 48), result)
    }

    private fun chapters(): List<Chapter> = listOf(
        Chapter(
            bookId = "book",
            chapterIndex = 0,
            title = "前言",
            startOffset = 0,
            endOffset = 30,
            wordCount = 30,
        ),
        Chapter(
            bookId = "book",
            chapterIndex = 1,
            title = "第1章",
            startOffset = 30,
            endOffset = 430,
            wordCount = 400,
        ),
        Chapter(
            bookId = "book",
            chapterIndex = 2,
            title = "第2章",
            startOffset = 430,
            endOffset = 670,
            wordCount = 240,
        ),
    )
}
