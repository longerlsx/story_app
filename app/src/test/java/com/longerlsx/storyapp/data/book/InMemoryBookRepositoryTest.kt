package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InMemoryBookRepositoryTest {

    @Test
    fun importedBooksAreReturnedInReverseLastReadOrder() = runTest {
        val repository = InMemoryBookRepository()
        val olderBook = sampleBook(
            id = "book-older",
            title = "旧书",
            lastReadAt = 100L,
        )
        val newerBook = sampleBook(
            id = "book-newer",
            title = "新书",
            lastReadAt = 300L,
        )

        repository.saveImportedBook(
            book = olderBook,
            chapters = listOf(sampleChapter(bookId = olderBook.id)),
            chapterContents = mapOf(0 to "旧书正文"),
        )
        repository.saveImportedBook(
            book = newerBook,
            chapters = listOf(sampleChapter(bookId = newerBook.id)),
            chapterContents = mapOf(0 to "新书正文"),
        )

        val bookshelf = repository.observeBookshelf().first()

        assertEquals(listOf("book-newer", "book-older"), bookshelf.map(Book::id))
    }

    @Test
    fun saveAndRestoreAnchorReturnsLatestOffset() = runTest {
        val repository = InMemoryBookRepository()
        val book = sampleBook(id = "book-anchor", title = "锚点书")

        repository.saveImportedBook(
            book = book,
            chapters = listOf(sampleChapter(bookId = book.id)),
            chapterContents = mapOf(0 to "正文"),
        )

        repository.saveReadingProgress(
            ReadingProgress(
                bookId = book.id,
                anchor = ReadingAnchor(chapterIndex = 0, charOffset = 18),
                readingMode = ReadingMode.SCROLL,
                updatedAt = 100L,
            ),
        )
        repository.saveReadingProgress(
            ReadingProgress(
                bookId = book.id,
                anchor = ReadingAnchor(chapterIndex = 0, charOffset = 144),
                readingMode = ReadingMode.SCROLL,
                updatedAt = 300L,
            ),
        )

        val restored = repository.getReadingProgress(book.id)

        assertNotNull(restored)
        assertEquals(ReadingAnchor(chapterIndex = 0, charOffset = 144), restored!!.anchor)
    }

    private fun sampleBook(
        id: String,
        title: String,
        lastReadAt: Long = 0L,
    ): Book = Book(
        id = id,
        title = title,
        author = "作者",
        importSourceType = ImportSourceType.LOCAL_FILE,
        importFileName = "$title.txt",
        storedPath = "/books/$id/original.txt",
        charset = "UTF-8",
        fileHash = "$id-hash",
        wordCount = 1024,
        chapterCount = 1,
        importedAt = 10L,
        lastReadAt = lastReadAt,
    )

    private fun sampleChapter(bookId: String): Chapter = Chapter(
        bookId = bookId,
        chapterIndex = 0,
        title = "第1章 开始",
        startOffset = 0,
        endOffset = 20,
        wordCount = 20,
    )
}
