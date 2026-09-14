package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCoordinatorTest {

    @Test
    fun doubleBomImportPreservesBodyAcrossDiskRestore() = runTest {
        val root = Files.createTempDirectory("story-app-import-double-bom").toFile()
        try {
            val storage = ImportedBookStorage(root)
            val repository = InMemoryBookRepository()
            val imported = ImportCoordinator(repository, storage, TextContentLoader()).importTxt(
                "双BOM.txt", "\uFEFF\uFEFF第1章 开始\n甲乙".encodeToByteArray(), ImportSourceType.LOCAL_FILE,
            )
            val importedBodies = imported.chapters.map {
                repository.getChapterText(imported.book.id, it.chapterIndex)
            }
            val restored = InMemoryBookRepository()
            restored.hydrateFromStorage(storage.restoreCatalog(), TextContentLoader())
            val restoredBodies = restored.getChapters(imported.book.id).map {
                restored.getChapterText(imported.book.id, it.chapterIndex)
            }

            assertEquals(importedBodies, restoredBodies)
            assertTrue(restoredBodies.joinToString("\n").contains("甲乙"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun whitespaceOnlyImportFailsWithoutPublishingAnUnreadableBook() = runTest {
        val root = Files.createTempDirectory("story-app-import-whitespace").toFile()
        try {
            val storage = ImportedBookStorage(root)
            val repository = InMemoryBookRepository()
            val result = runCatching {
                ImportCoordinator(repository, storage, TextContentLoader()).importTxt(
                    "空白.txt", " \n\t\r\n　".encodeToByteArray(), ImportSourceType.LOCAL_FILE,
                )
            }

            assertTrue(result.isFailure)
            assertTrue(repository.observeBookshelf().first().isEmpty())
            assertTrue(storage.restoreCatalog().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedRepositoryPublicationDoesNotLeaveRestorablePartialImport() = runTest {
        val root = Files.createTempDirectory("story-app-import-rollback").toFile()
        try {
            val delegate = InMemoryBookRepository()
            val repository = object : BookRepository by delegate {
                override suspend fun saveImportedBook(book: Book, chapters: List<Chapter>, chapterContents: Map<Int, String>) {
                    throw IOException("publication failed")
                }
            }
            val storage = ImportedBookStorage(root)
            val coordinator = ImportCoordinator(repository, storage, TextContentLoader())

            val result = runCatching {
                coordinator.importTxt("失败.txt", "第1章 开始\n正文。".encodeToByteArray(), ImportSourceType.LOCAL_FILE)
            }

            assertTrue(result.isFailure)
            assertTrue(delegate.observeBookshelf().first().isEmpty())
            assertTrue(storage.restoreCatalog().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentImportsOfSameTextPublishOnlyOneNewBook() = runTest {
        val root = Files.createTempDirectory("story-app-import-concurrent").toFile()
        try {
            val delegate = InMemoryBookRepository()
            val repository = object : BookRepository by delegate {
                override suspend fun findBookByHash(fileHash: String): Book? {
                    val result = delegate.findBookByHash(fileHash)
                    delay(1)
                    return result
                }
            }
            val storage = ImportedBookStorage(root)
            val coordinator = ImportCoordinator(repository, storage, TextContentLoader())
            val bytes = "第1章 开始\n同一份正文。".encodeToByteArray()
            val first = async { coordinator.importTxt("一.txt", bytes, ImportSourceType.LOCAL_FILE) }
            val second = async { coordinator.importTxt("二.txt", bytes, ImportSourceType.EXTERNAL_INTENT) }
            val results = listOf(first.await(), second.await())

            assertEquals(1, results.count { !it.duplicate })
            assertEquals(1, results.count { it.duplicate })
            assertEquals(1, storage.restoreCatalog().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importTxtParsesAndStoresBook() = runTest {
        val tempDir = Files.createTempDirectory("story-app-import-test")
        try {
            val repository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = repository,
                storage = ImportedBookStorage(tempDir.toFile()),
                textContentLoader = TextContentLoader(),
            )

            val sample = """
                推荐一个小说下载必备网址：www.799txt.com
                《测试小说》
                作者：测试作者

                这是前言。

                第1章 开始
                正文第一段。
            """.trimIndent().encodeToByteArray()

            val result = coordinator.importTxt(
                fileName = "《测试小说》作者：测试作者.txt",
                bytes = sample,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )

            assertFalse(result.duplicate)
            assertEquals("测试小说", result.book.title)
            assertEquals("测试作者", result.book.author)
            assertEquals(2, result.chapters.size)
            assertTrue(result.chapters.first().title == "前言")
            assertEquals(listOf("测试小说"), repository.observeBookshelf().first().map { it.title })
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun importTxtReturnsExistingBookWhenHashAlreadyExists() = runTest {
        val tempDir = Files.createTempDirectory("story-app-import-duplicate-test")
        try {
            val repository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = repository,
                storage = ImportedBookStorage(tempDir.toFile()),
                textContentLoader = TextContentLoader(),
            )
            val bytes = """
                《重复小说》
                作者：测试作者

                第1章 重复
                同一份正文。
            """.trimIndent().encodeToByteArray()

            val first = coordinator.importTxt(
                fileName = "重复小说.txt",
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )
            val second = coordinator.importTxt(
                fileName = "重复小说-副本.txt",
                bytes = bytes,
                sourceType = ImportSourceType.EXTERNAL_INTENT,
                importedAt = 200L,
            )

            assertFalse(first.duplicate)
            assertTrue(second.duplicate)
            assertEquals(first.book.id, second.book.id)
            assertEquals(1, repository.observeBookshelf().first().size)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun importTxtStoresLaterChapterContentForReaderJump() = runTest {
        val tempDir = Files.createTempDirectory("story-app-import-multi-chapter-test")
        try {
            val repository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = repository,
                storage = ImportedBookStorage(tempDir.toFile()),
                textContentLoader = TextContentLoader(),
            )
            val bytes = """
                《目录测试》
                作者：测试作者

                第1章 开始
                第一章正文。

                第2章 继续
                第二章正文。
            """.trimIndent().encodeToByteArray()

            val result = coordinator.importTxt(
                fileName = "目录测试.txt",
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )

            val chapterTwo = result.chapters.first { it.title == "第2章 继续" }

            assertEquals("第二章正文。", repository.getChapterText(result.book.id, chapterTwo.chapterIndex))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun importTxtStoresBodyOffsetsForDoubleTitleChapters() = runTest {
        val tempDir = Files.createTempDirectory("story-app-import-double-title-test")
        try {
            val repository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = repository,
                storage = ImportedBookStorage(tempDir.toFile()),
                textContentLoader = TextContentLoader(),
            )
            val bytes = """
                《双标题测试》
                作者：测试作者

                第 1 章
                第一章
                正文第一段。
            """.trimIndent().encodeToByteArray()

            val result = coordinator.importTxt(
                fileName = "双标题测试.txt",
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )

            val chapter = result.chapters.first { it.title == "第 1 章" }
            val normalized = TxtNormalizer.normalize(bytes.decodeToString())

            assertEquals("正文第一段。", repository.getChapterText(result.book.id, chapter.chapterIndex))
            assertEquals("正文第一段。".length, chapter.wordCount)
            assertEquals("正文第一段。", normalized.substring(chapter.startOffset, chapter.endOffset))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun importTxtStoresSubtitlePunctuationChapterTitles() = runTest {
        val tempDir = Files.createTempDirectory("story-app-import-subtitle-punctuation-test")
        try {
            val repository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = repository,
                storage = ImportedBookStorage(tempDir.toFile()),
                textContentLoader = TextContentLoader(),
            )
            val bytes = """
                《副标题测试》
                作者：测试作者

                第1章 独行者1:“杀人犯终于被抓住了！”
                正文一。

                第2章 标题：副题？
                正文二。
            """.trimIndent().encodeToByteArray()

            val result = coordinator.importTxt(
                fileName = "副标题测试.txt",
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )

            val firstChapter = result.chapters.first { it.title == "第1章 独行者1:“杀人犯终于被抓住了！”" }
            val secondChapter = result.chapters.first { it.title == "第2章 标题：副题？" }
            val normalized = TxtNormalizer.normalize(bytes.decodeToString())

            assertEquals(
                listOf("第1章 独行者1:“杀人犯终于被抓住了！”", "第2章 标题：副题？"),
                result.chapters.map { it.title }.filterNot { it == "前言" },
            )
            assertEquals("正文一。", repository.getChapterText(result.book.id, firstChapter.chapterIndex))
            assertEquals("正文二。", repository.getChapterText(result.book.id, secondChapter.chapterIndex))
            assertEquals("正文一。", normalized.substring(firstChapter.startOffset, firstChapter.endOffset))
            assertEquals("正文二。", normalized.substring(secondChapter.startOffset, secondChapter.endOffset))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun importTxtStoresNumberedSubtitlePunctuationChapterTitles() = runTest {
        val tempDir = Files.createTempDirectory("story-app-import-numbered-subtitle-punctuation-test")
        try {
            val repository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = repository,
                storage = ImportedBookStorage(tempDir.toFile()),
                textContentLoader = TextContentLoader(),
            )
            val bytes = """
                《数字副标题测试》
                作者：测试作者

                第1章 001 系统都能绑错？
                正文一。

                第51章 050 丹道比试，第一！
                正文二。
            """.trimIndent().encodeToByteArray()

            val result = coordinator.importTxt(
                fileName = "数字副标题测试.txt",
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )

            val firstChapter = result.chapters.first { it.title == "第1章 001 系统都能绑错？" }
            val secondChapter = result.chapters.first { it.title == "第51章 050 丹道比试，第一！" }
            val normalized = TxtNormalizer.normalize(bytes.decodeToString())

            assertEquals(
                listOf("第1章 001 系统都能绑错？", "第51章 050 丹道比试，第一！"),
                result.chapters.map { it.title }.filterNot { it == "前言" },
            )
            assertEquals("正文一。", repository.getChapterText(result.book.id, firstChapter.chapterIndex))
            assertEquals("正文二。", repository.getChapterText(result.book.id, secondChapter.chapterIndex))
            assertEquals("正文一。", normalized.substring(firstChapter.startOffset, firstChapter.endOffset))
            assertEquals("正文二。", normalized.substring(secondChapter.startOffset, secondChapter.endOffset))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun importTxtStoresNumberedChapterTitleWhenRepeatedSpecialSubtitleFollows() = runTest {
        val tempDir = Files.createTempDirectory("story-app-import-special-double-title-test")
        try {
            val repository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = repository,
                storage = ImportedBookStorage(tempDir.toFile()),
                textContentLoader = TextContentLoader(),
            )
            val bytes = """
                《番外双标题测试》
                作者：测试作者

                第141章 番外一
                　　番外一
                N年以后。

                第142章 番外二
                　　番外二
                谢钊很迅速地起了床。
            """.trimIndent().encodeToByteArray()

            val result = coordinator.importTxt(
                fileName = "番外双标题测试.txt",
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )

            val firstChapter = result.chapters.first { it.title == "第141章 番外一" }
            val secondChapter = result.chapters.first { it.title == "第142章 番外二" }
            val normalized = TxtNormalizer.normalize(bytes.decodeToString())

            assertEquals(
                listOf("第141章 番外一", "第142章 番外二"),
                result.chapters.map { it.title }.filterNot { it == "前言" },
            )
            assertEquals("N年以后。", repository.getChapterText(result.book.id, firstChapter.chapterIndex))
            assertEquals("谢钊很迅速地起了床。", repository.getChapterText(result.book.id, secondChapter.chapterIndex))
            assertEquals("N年以后。", normalized.substring(firstChapter.startOffset, firstChapter.endOffset))
            assertEquals("谢钊很迅速地起了床。", normalized.substring(secondChapter.startOffset, secondChapter.endOffset))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun importTxtDecodesGbkChineseTextBeforeMetadataAndChapterParsing() = runTest {
        val tempDir = Files.createTempDirectory("story-app-import-gbk-test")
        try {
            val repository = InMemoryBookRepository()
            val coordinator = ImportCoordinator(
                repository = repository,
                storage = ImportedBookStorage(tempDir.toFile()),
                textContentLoader = TextContentLoader(),
            )
            val content = """
                《编码测试》
                作者：编码作者

                第1章 开始
                中文正文第一段。
            """.trimIndent()
            val bytes = content.toByteArray(Charset.forName("GBK"))

            val result = coordinator.importTxt(
                fileName = "编码测试.txt",
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
                importedAt = 100L,
            )

            val chapter = result.chapters.first { it.title == "第1章 开始" }

            assertEquals("编码测试", result.book.title)
            assertEquals("编码作者", result.book.author)
            assertEquals("GB18030", result.book.charset)
            assertEquals("中文正文第一段。", repository.getChapterText(result.book.id, chapter.chapterIndex))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
