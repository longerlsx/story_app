package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningChapterSelectorTest {

    @Test
    fun defaultsToFirstNonPrefaceChapterWhenNoProgressExists() {
        val chapters = listOf(
            Chapter(bookId = "book-1", chapterIndex = 0, title = "前言", startOffset = 0, endOffset = 10, wordCount = 10),
            Chapter(bookId = "book-1", chapterIndex = 1, title = "第1章 开始", startOffset = 11, endOffset = 40, wordCount = 29),
        )

        val chapterIndex = OpeningChapterSelector.select(
            chapters = chapters,
            progress = null,
        )

        assertEquals(1, chapterIndex)
    }

    @Test
    fun prefersSavedProgressWhenAvailable() {
        val chapters = listOf(
            Chapter(bookId = "book-1", chapterIndex = 0, title = "前言", startOffset = 0, endOffset = 10, wordCount = 10),
            Chapter(bookId = "book-1", chapterIndex = 1, title = "第1章 开始", startOffset = 11, endOffset = 40, wordCount = 29),
            Chapter(bookId = "book-1", chapterIndex = 2, title = "第2章 继续", startOffset = 41, endOffset = 80, wordCount = 39),
        )

        val chapterIndex = OpeningChapterSelector.select(
            chapters = chapters,
            progress = ReadingProgress(
                bookId = "book-1",
                anchor = ReadingAnchor(chapterIndex = 2, charOffset = 15),
                readingMode = ReadingMode.SCROLL,
                updatedAt = 200L,
            ),
        )

        assertEquals(2, chapterIndex)
    }
}
