package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.feature.reader.tts.ReaderTextStartLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsStartRequestFactoryTest {

    @Test
    fun createsRequestWithBookAndChapterContext() {
        val request = ReaderTtsStartRequestFactory.create(
            book = book(),
            chapters = listOf(
                chapter(chapterIndex = 0, title = "序章"),
                chapter(chapterIndex = 2, title = "第二章 风起"),
            ),
            startLocation = ReaderTextStartLocation(
                chapterIndex = 2,
                charOffset = 128,
            ),
        )

        assertEquals("book-1", request.bookId)
        assertEquals("长篇测试", request.bookTitle)
        assertEquals(2, request.chapterIndex)
        assertEquals(128, request.charOffset)
        assertEquals("第二章 风起", request.chapterTitleOrSummary)
        assertEquals("朗读中", request.activeStateLabel)
    }

    @Test
    fun fallsBackToBodySummaryWhenChapterTitleIsMissing() {
        val request = ReaderTtsStartRequestFactory.create(
            book = book(),
            chapters = listOf(chapter(chapterIndex = 0, title = "序章")),
            startLocation = ReaderTextStartLocation(
                chapterIndex = 7,
                charOffset = 32,
            ),
        )

        assertEquals("正文", request.chapterTitleOrSummary)
    }

    private fun book(): Book {
        return Book(
            id = "book-1",
            title = "长篇测试",
            author = "作者",
            importSourceType = ImportSourceType.LOCAL_FILE,
            importFileName = "novel.txt",
            storedPath = "/tmp/novel.txt",
            charset = "UTF-8",
            fileHash = "hash",
            wordCount = 1000,
            chapterCount = 3,
            importedAt = 1L,
            lastReadAt = 2L,
        )
    }

    private fun chapter(
        chapterIndex: Int,
        title: String,
    ): Chapter {
        return Chapter(
            bookId = "book-1",
            chapterIndex = chapterIndex,
            title = title,
            startOffset = chapterIndex * 100,
            endOffset = chapterIndex * 100 + 99,
            wordCount = 99,
        )
    }
}
