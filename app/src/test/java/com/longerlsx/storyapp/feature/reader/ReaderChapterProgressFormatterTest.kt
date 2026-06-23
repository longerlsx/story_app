package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChapterProgressFormatterTest {

    @Test
    fun excludesSyntheticPrefaceFromNumberedChapterProgress() {
        val chapters = listOf(
            chapter(index = 0, title = "前言"),
            chapter(index = 1, title = "第1章 乘船"),
            chapter(index = 2, title = "第2章 抵达"),
        )

        assertEquals("1/2章", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 1))
        assertEquals("2/2章", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 2))
    }

    @Test
    fun keepsRegularPositionWhenThereIsNoSyntheticPreface() {
        val chapters = listOf(
            chapter(index = 0, title = "第1章 乘船"),
            chapter(index = 1, title = "第2章 抵达"),
        )

        assertEquals("1/2章", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 0))
    }

    @Test
    fun countsNumericChapterTitlesAfterSyntheticPreface() {
        val chapters = listOf(
            chapter(index = 0, title = "前言"),
            chapter(index = 1, title = "1. 初见"),
            chapter(index = 2, title = "2. 重逢"),
        )

        assertEquals("1/2章", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 1))
    }

    @Test
    fun countsSpecialChapterTitlesAfterSyntheticPreface() {
        val chapters = listOf(
            chapter(index = 0, title = "前言"),
            chapter(index = 1, title = "序章"),
            chapter(index = 2, title = "第1章 开始"),
            chapter(index = 3, title = "番外一 日常"),
        )

        assertEquals("1/3章", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 1))
        assertEquals("3/3章", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 3))
    }

    @Test
    fun keepsFallbackBodyChaptersCountedByPosition() {
        val chapters = listOf(
            chapter(index = 0, title = "正文 1"),
            chapter(index = 1, title = "正文 2"),
        )

        assertEquals("1/2章", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 0))
        assertEquals("2/2章", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 1))
    }

    @Test
    fun describesSyntheticPrefaceWithoutPretendingItIsAChapterNumber() {
        val chapters = listOf(
            chapter(index = 0, title = "前言"),
            chapter(index = 1, title = "第1章 乘船"),
        )

        assertEquals("前言", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 0))
    }

    @Test
    fun returnsPlaceholderForInvalidSelection() {
        val chapters = listOf(chapter(index = 0, title = "第1章 开始"))

        assertEquals("--/--", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = -1))
        assertEquals("--/--", ReaderChapterProgressFormatter.format(chapters, selectedChapterPosition = 1))
        assertEquals("--/--", ReaderChapterProgressFormatter.format(emptyList(), selectedChapterPosition = 0))
    }

    private fun chapter(
        index: Int,
        title: String,
    ): Chapter {
        return Chapter(
            bookId = "book",
            chapterIndex = index,
            title = title,
            startOffset = index * 100,
            endOffset = index * 100 + 80,
            wordCount = 80,
        )
    }
}
