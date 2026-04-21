package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderActiveChapterResolverTest {

    @Test
    fun prefersFirstVisibleNonPrefaceChapterWhenPrefaceIsStillAtTop() {
        val result = ReaderActiveChapterResolver.resolve(
            visibleItems = listOf(
                ReaderVisibleChapterItem(itemIndex = 0, chapterIndex = 0, offsetPx = 0, sizePx = 340),
                ReaderVisibleChapterItem(itemIndex = 1, chapterIndex = 1, offsetPx = 340, sizePx = 256),
                ReaderVisibleChapterItem(itemIndex = 2, chapterIndex = 2, offsetPx = 596, sizePx = 400),
            ),
            chapters = listOf(
                Chapter(bookId = "book", chapterIndex = 0, title = "前言", startOffset = 0, endOffset = 12, wordCount = 4),
                Chapter(bookId = "book", chapterIndex = 1, title = "第1章 开始", startOffset = 12, endOffset = 200, wordCount = 120),
                Chapter(bookId = "book", chapterIndex = 2, title = "第2章 继续", startOffset = 200, endOffset = 320, wordCount = 80),
            ),
            viewportTopPx = 0,
        )

        assertEquals(1, result)
    }

    @Test
    fun keepsRegularChapterWhenTopChapterIsNotPreface() {
        val result = ReaderActiveChapterResolver.resolve(
            visibleItems = listOf(
                ReaderVisibleChapterItem(itemIndex = 1, chapterIndex = 1, offsetPx = 0, sizePx = 460),
                ReaderVisibleChapterItem(itemIndex = 2, chapterIndex = 2, offsetPx = 460, sizePx = 420),
            ),
            chapters = listOf(
                Chapter(bookId = "book", chapterIndex = 1, title = "第1章 开始", startOffset = 0, endOffset = 200, wordCount = 120),
                Chapter(bookId = "book", chapterIndex = 2, title = "第2章 继续", startOffset = 200, endOffset = 320, wordCount = 80),
            ),
            viewportTopPx = 0,
        )

        assertEquals(1, result)
    }
}
