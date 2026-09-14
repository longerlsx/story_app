package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import java.nio.file.Files
import java.io.File
import org.junit.Assert.assertArrayEquals
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedBookCatalogRestoreTest {

    @Test
    fun restoresLegacyTabTitlesWithoutRewritingCatalog() = runTest {
        val root = Files.createTempDirectory("story-app-legacy-tabs").toFile()
        try {
            val storage = ImportedBookStorage(root)
            val imported = ImportCoordinator(InMemoryBookRepository(), storage, TextContentLoader()).importTxt(
                fileName = "制表符.txt",
                bytes = "第1章\t开始\t标题\n正文一。\n第2章 继续\n正文二。".encodeToByteArray(),
                sourceType = ImportSourceType.LOCAL_FILE,
            )
            val filesBefore = root.walkTopDown().filter(File::isFile).associateWith(File::readBytes)

            val restored = storage.restoreCatalog().single()

            assertEquals(listOf("第1章\t开始\t标题", "第2章 继续"), restored.chapters.map { it.title })
            assertEquals(imported.chapters, restored.chapters)
            filesBefore.forEach { (file, bytes) -> assertArrayEquals(file.path, bytes, file.readBytes()) }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsWholeMalformedCatalogWithoutLosingHealthyBooks() {
        val invalidCatalogs = listOf(
            "0\t第1章\t0\t5\t5\n破损行\n",
            "0\t第1章\t0\t5\t5\n1\t第2章\t错误\t10\t5\n",
            "0\t第1章\t0\t5\t5\n0\t第2章\t5\t10\t5\n",
            "0\t第1章\t0\t8\t5\n1\t第2章\t5\t10\t5\n",
            "0\t第1章\t-1\t5\t5\n1\t第2章\t5\t10\t5\n",
            "0\t第1章\t0\t5\t5\n",
        )
        invalidCatalogs.forEach { invalid ->
            val root = Files.createTempDirectory("story-app-damaged-catalog").toFile()
            try {
                val storage = ImportedBookStorage(root)
                persistTwoChapterFixture(storage, "healthy")
                persistTwoChapterFixture(storage, "damaged")
                root.resolve("books/damaged/chapters.tsv").writeText(invalid)

                assertEquals(listOf("healthy"), storage.restoreCatalog().map { it.book.id })
                assertTrue(root.resolve("books/damaged/original.txt").exists())
            } finally {
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun doesNotCommitMetadataWhenWritingChaptersFails() {
        val root = Files.createTempDirectory("story-app-catalog-write-failure").toFile()
        try {
            root.resolve("books/damaged/chapters.tsv/obstruction").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("keep")
            }

            val result = runCatching { persistTwoChapterFixture(ImportedBookStorage(root), "damaged") }

            assertTrue(result.isFailure)
            assertFalse(root.resolve("books/damaged/metadata.properties").exists())
        } finally {
            root.deleteRecursively()
        }
    }

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

    private fun persistTwoChapterFixture(storage: ImportedBookStorage, id: String) {
        val source = storage.copyImportedFile(id, "甲乙丙丁戊己庚辛壬癸".encodeToByteArray())
        storage.persistCatalog(
            Book(id, id, null, ImportSourceType.LOCAL_FILE, "$id.txt", source.absolutePath,
                "UTF-8", "hash-$id", 10, 2, 1L, 1L),
            listOf(
                Chapter(id, 0, "第1章", 0, 5, 5),
                Chapter(id, 1, "第2章", 5, 10, 5),
            ),
        )
    }
}
