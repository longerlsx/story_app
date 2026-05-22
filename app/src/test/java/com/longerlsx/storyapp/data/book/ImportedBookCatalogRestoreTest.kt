package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
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

    @Test
    fun restoredChapterTextMatchesImportedBodyTextForDoubleTitles() = runTest {
        val tempDir = Files.createTempDirectory("story-app-catalog-double-title-restore-test").toFile()
        try {
            val storage = ImportedBookStorage(tempDir)
            val firstRepository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = firstRepository,
                storage = storage,
                textContentLoader = TextContentLoader(),
            )
            val bytes = """
                《恢复双标题测试》
                作者：测试作者

                第 1 章
                第一章
                正文第一段。
            """.trimIndent().encodeToByteArray()

            val imported = coordinator.importTxt(
                fileName = "恢复双标题测试.txt",
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )
            val chapter = imported.chapters.first { it.title == "第 1 章" }
            val originalBody = firstRepository.getChapterText(imported.book.id, chapter.chapterIndex)

            val restoredRepository = InMemoryBookRepository()
            restoredRepository.hydrateFromStorage(
                snapshots = storage.restoreCatalog(),
                textContentLoader = TextContentLoader(),
            )

            assertEquals(originalBody, restoredRepository.getChapterText(imported.book.id, chapter.chapterIndex))
            assertEquals("正文第一段。", restoredRepository.getChapterText(imported.book.id, chapter.chapterIndex))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun hydrateFromStoragePreservesExactOffsetSliceWithoutTrimming() = runTest {
        val tempDir = Files.createTempDirectory("story-app-catalog-exact-slice-test").toFile()
        try {
            val storage = ImportedBookStorage(tempDir)
            val body = "  正文  "
            val raw = "标题\n$body\n"
            val bodyStart = raw.indexOf(body)
            val book = Book(
                id = "book-exact",
                title = "精确切片",
                author = "测试作者",
                importSourceType = ImportSourceType.LOCAL_FILE,
                importFileName = "精确切片.txt",
                storedPath = tempDir.resolve("books/book-exact/original.txt").absolutePath,
                charset = "UTF-8",
                fileHash = "hash-exact",
                wordCount = raw.filterNot(Char::isWhitespace).length,
                chapterCount = 1,
                importedAt = 100L,
                lastReadAt = 100L,
            )
            val chapters = listOf(
                Chapter(
                    bookId = book.id,
                    chapterIndex = 0,
                    title = "正文",
                    startOffset = bodyStart,
                    endOffset = bodyStart + body.length,
                    wordCount = body.filterNot(Char::isWhitespace).length,
                ),
            )

            storage.copyImportedFile(book.id, raw.encodeToByteArray())
            storage.persistCatalog(book, chapters)

            val repository = InMemoryBookRepository()
            repository.hydrateFromStorage(storage.restoreCatalog(), TextContentLoader())

            assertEquals(body, repository.getChapterText(book.id, 0))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
