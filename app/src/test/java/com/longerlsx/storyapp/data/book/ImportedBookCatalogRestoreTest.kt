package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedBookCatalogRestoreTest {

    @Test
    fun persistsAndRestoresBookCatalogFromDisk() {
        val tempDir = Files.createTempDirectory("story-app-catalog-restore-test").toFile()
        try {
            val storage = ImportedBookStorage(tempDir)
            val book = Book(
                id = "book-1",
                title = "恢复测试",
                author = "测试作者",
                importSourceType = ImportSourceType.LOCAL_FILE,
                importFileName = "恢复测试.txt",
                storedPath = tempDir.resolve("books/book-1/original.txt").absolutePath,
                charset = "UTF-8",
                fileHash = "hash-1",
                wordCount = 128,
                chapterCount = 2,
                importedAt = 100L,
                lastReadAt = 100L,
            )
            val chapters = listOf(
                Chapter(bookId = "book-1", chapterIndex = 0, title = "前言", startOffset = 0, endOffset = 8, wordCount = 8),
                Chapter(bookId = "book-1", chapterIndex = 1, title = "第1章 开始", startOffset = 9, endOffset = 20, wordCount = 11),
            )

            storage.copyImportedFile("book-1", "这是前言\n第1章 开始\n正文".encodeToByteArray())
            storage.persistCatalog(book, chapters)

            val restored = storage.restoreCatalog()

            assertEquals(1, restored.size)
            assertEquals("恢复测试", restored.first().book.title)
            assertEquals(listOf("前言", "第1章 开始"), restored.first().chapters.map { it.title })
            assertTrue(restored.first().sourceFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
