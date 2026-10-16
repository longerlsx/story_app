package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserExternalCorpusTest {

    @Test
    fun calibratesProductionImportAndRestoreWithOneExternalNovel() = runTest {
        val file = externalCorpusFiles().firstOrNull { it.name.contains("小猫咪在星际监狱") }
        assumeTrue("Calibration novel is not available on this machine", file != null)

        verifyImportAndRestore(listOf(requireNotNull(file))) { _, result ->
            assertTrue(result.chapters.any { it.title == "第1章" })
            assertTrue(result.chapters.any { it.title == "第66章" })
        }
    }

    @Test
    fun parsesExternalNovelCorpusWithExactOffsets() = runTest {
        val files = externalCorpusFiles()

        var checkedPlayersGuide = false
        var checkedXianyuSystem = false
        var checkedGuawang = false
        var checkedAncientConstruction = false
        var checkedDoomsdayPartner = false
        var checkedSpaceCat = false
        var checkedXiuluochang = false
        var checkedHospitalPalace = false
        var checkedBraisedPork = false
        verifyImportAndRestore(files) { file, result ->
            if (file.name.contains("玩家救世指南")) {
                checkedPlayersGuide = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第2章 独行者2:穿越者？玩家？神？"))
                assertTrue(titles.contains("第84章 幸福玛丽孤儿院1:“我愿意在你的胞宫中沉睡，直到世界的纱幕再一次掀起……”"))
                assertFalse(titles.contains("第十三回 ……"))
                assertTrue(result.chapters.single { it.title.startsWith("第26章 ") }.content.contains("第十三回 ……"))
            }
            if (file.name.contains("咸鱼和反派错绑对方系统后")) {
                checkedXianyuSystem = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第1章 001 系统都能绑错？"))
                assertTrue(titles.contains("第51章 050 丹道比试，第一！"))
                assertTrue(titles.contains("第71章 070 他们也跟我一样喜欢她？"))
                assertTrue(titles.contains("第94章 093 有没有可能，是剑龙？"))
                assertTrue(titles.contains("第122章 121 嗯呐么哒。么么。"))
            }
            if (file.name.contains("星际第一种瓜王")) {
                checkedGuawang = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第6章 006 好好吃！"))
                assertTrue(titles.contains("第22章 022 全跑了！"))
                assertTrue(titles.contains("第34章 034 穷鬼退散！"))
                assertTrue(titles.contains("第110章 110 讨好我！"))
                assertTrue(titles.contains("第141章 番外一"))
                assertTrue(titles.contains("第142章 番外二"))
                assertTrue(result.chapters.single { it.title == "第141章 番外一" }.content.startsWith("N年以后。"))
                assertTrue(result.chapters.single { it.title == "第142章 番外二" }.content.startsWith("谢钊很迅速地起了床"))
            }
            if (file.name.contains("我在古代搞建设")) {
                checkedAncientConstruction = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第95章 ……"))
                assertTrue(titles.contains("第107章 ……"))
                assertTrue(titles.contains("第140章 ……"))
                assertTrue(titles.contains("第176章 番外被骗了……"))
                assertTrue(titles.contains("第177章 关于嫁儿子……"))
                assertTrue(titles.contains("第178章 关于嫁儿子……"))
            }
            if (file.name.contains("末世反派是我的工作搭子")) {
                checkedDoomsdayPartner = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第1章"))
                assertTrue(titles.contains("第80章"))
                assertEquals("番外", result.chapters.last().title)
                assertTrue(result.chapters.last().content.startsWith("职场系统被收拾了后"))
            }
            if (file.name.contains("小猫咪在星际监狱")) {
                checkedSpaceCat = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第1章"))
                assertTrue(titles.contains("第66章"))
            }
            if (file.name.contains("这该死的修罗场")) {
                checkedXiuluochang = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第59章"))
                assertTrue(titles.contains("第65章 黄金城之战（上）"))
                assertTrue(titles.contains("第66章 黄金城之战（中）"))
                assertTrue(titles.contains("第67章 黄金城之战（下）"))
                assertTrue(titles.contains("第77章 大结局"))
            }
            if (file.name.contains("这座仙宫叫医院")) {
                checkedHospitalPalace = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第1章"))
                assertTrue(titles.contains("第120章"))
            }
            if (file.name.contains("半夜想吃前任做的红烧肉怎么办")) {
                checkedBraisedPork = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第1章 第一块红烧肉"))
                assertTrue(titles.contains("第31章 吃……第一口"))
                assertTrue(titles.contains("第60章 吃……第三十口"))
                assertTrue(titles.contains("第68章 正文完"))
            }
        }

        assertTrue("External corpus should include 玩家救世指南 target file", checkedPlayersGuide)
        assertTrue("External corpus should include 咸鱼和反派错绑对方系统后 target file", checkedXianyuSystem)
        assertTrue("External corpus should include 星际第一种瓜王 target file", checkedGuawang)
        assertTrue("External corpus should include 我在古代搞建设 target file", checkedAncientConstruction)
        assertTrue("External corpus should include 末世反派是我的工作搭子 target file", checkedDoomsdayPartner)
        assertTrue("External corpus should include 小猫咪在星际监狱 target file", checkedSpaceCat)
        assertTrue("External corpus should include 这该死的修罗场 target file", checkedXiuluochang)
        assertTrue("External corpus should include 这座仙宫叫医院 target file", checkedHospitalPalace)
        assertTrue("External corpus should include 半夜想吃前任做的红烧肉怎么办 target file", checkedBraisedPork)
    }

    private fun externalCorpusFiles(): List<File> {
        val corpusRoot = File("/Users/longshengxi/Downloads/小说测试集")
        assumeTrue("External novel corpus is not available on this machine", corpusRoot.isDirectory)
        val files = corpusRoot.walkTopDown()
            .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            .sortedBy(File::getName)
            .toList()
        assumeTrue("External novel corpus has no txt files", files.isNotEmpty())
        return files
    }

    private suspend fun verifyImportAndRestore(
        files: List<File>,
        verifyKnownHeadings: (File, ChapterParseResult) -> Unit,
    ) {
        val root = Files.createTempDirectory("story-app-external-corpus").toFile()
        try {
            val loader = TextContentLoader()
            val storage = ImportedBookStorage(root)
            val repository = InMemoryBookRepository(FileAnchorStore(root))
            val coordinator = ImportCoordinator(repository, storage, loader)
            val expected = files.mapIndexed { fileIndex, file ->
                // Use the same decoder as import, including BOM and GB18030 handling.
                val loaded = loader.loadNormalizedText(file)
                val normalized = loaded.normalizedText
                val parsed = ChapterParser.parseDetailed(normalized)
                assertTrue("${file.name} should parse at least one chapter", parsed.chapters.isNotEmpty())
                assertReviewedChapterCatalog(file, normalized, parsed)
                var previousEnd = 0
                parsed.chapters.forEach { chapter ->
                    assertTrue("${file.name}: ${chapter.title} invalid start", chapter.startOffset in 0..normalized.length)
                    assertTrue(
                        "${file.name}: ${chapter.title} invalid end",
                        chapter.endOffset in chapter.startOffset..normalized.length,
                    )
                    assertTrue("${file.name}: ${chapter.title} overlaps", chapter.startOffset >= previousEnd)
                    assertEquals(
                        "${file.name}: ${chapter.title} exact source slice",
                        normalized.substring(chapter.startOffset, chapter.endOffset),
                        chapter.content,
                    )
                    previousEnd = chapter.endOffset
                }
                verifyKnownHeadings(file, parsed)

                val imported = coordinator.importTxt(
                    fileName = file.name,
                    bytes = file.readBytes(),
                    sourceType = ImportSourceType.LOCAL_FILE,
                    importedAt = 1_000L + fileIndex,
                )
                assertFalse("${file.name} should be a fresh import", imported.duplicate)
                assertEquals(loaded.charsetName, imported.book.charset)
                assertEquals(parsed.chapters.map { it.title }, imported.chapters.map { it.title })
                assertEquals(parsed.chapters.indices.toList(), imported.chapters.map { it.chapterIndex })
                imported.chapters.forEachIndexed { index, chapter ->
                    assertEquals(parsed.chapters[index].startOffset, chapter.startOffset)
                    assertEquals(parsed.chapters[index].endOffset, chapter.endOffset)
                    assertEquals(
                        "${file.name}: imported chapter $index body",
                        parsed.chapters[index].content,
                        repository.getChapterText(imported.book.id, index),
                    )
                }

                val middleChapter = parsed.chapters.size / 2
                val middleBody = parsed.chapters[middleChapter].content
                assertTrue("${file.name}: need a nonzero intra-chapter anchor", middleBody.length > 1)
                val progress = ReadingProgress(
                    bookId = imported.book.id,
                    anchor = ReadingAnchor(middleChapter, middleBody.length / 2),
                    readingMode = if (fileIndex % 2 == 0) ReadingMode.PAGE else ReadingMode.SCROLL,
                    updatedAt = 2_000L + fileIndex,
                )
                repository.saveReadingProgress(progress)
                ExpectedImport(file.name, imported, parsed.chapters, progress)
            }

            // Recreate both stores and the repository; never read the original repository's cache.
            val restoredStore = FileAnchorStore(root)
            val restoredStorage = ImportedBookStorage(root)
            val snapshots = restoredStorage.restoreCatalog()
            assertEquals(expected.map { it.imported.book.id }.toSet(), snapshots.map { it.book.id }.toSet())
            val restored = InMemoryBookRepository(restoredStore)
            restored.hydrateFromStorage(snapshots, TextContentLoader())
            restored.hydrateProgress(restoredStore.loadAll())
            expected.forEach { book ->
                val bookId = book.imported.book.id
                val restoredBook = requireNotNull(restored.getBook(bookId))
                assertEquals(book.imported.book.title, restoredBook.title)
                assertEquals(book.imported.book.charset, restoredBook.charset)
                assertEquals(book.imported.book.fileHash, restoredBook.fileHash)
                assertEquals("${book.fileName}: full ordered chapter catalog", book.imported.chapters, restored.getChapters(bookId))
                book.chapters.forEachIndexed { index, chapter ->
                    assertEquals(
                        "${book.fileName}: restored chapter $index body",
                        chapter.content,
                        restored.getChapterText(bookId, index),
                    )
                }
                assertEquals("${book.fileName}: intra-chapter progress", book.progress, restored.getReadingProgress(bookId))
                val anchor = book.progress.anchor
                val restoredBody = requireNotNull(restored.getChapterText(bookId, anchor.chapterIndex))
                assertEquals(
                    "${book.fileName}: text at restored anchor",
                    book.chapters[anchor.chapterIndex].content.substring(anchor.charOffset).take(48),
                    restoredBody.substring(anchor.charOffset).take(48),
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private data class ExpectedImport(
        val fileName: String,
        val imported: ImportResult,
        val chapters: List<ParsedChapter>,
        val progress: ReadingProgress,
    )

    private fun assertReviewedChapterCatalog(file: File, content: String, parsed: ChapterParseResult) {
        // These sequences were checked against these nine source books. In particular,
        // 咸鱼 jumps from chapter 1 to 3 in the TXT itself; it is not a parser omission.
        val expectedNumbers = when {
            file.name.contains("玩家救世指南") -> (1..200).toList()
            file.name.contains("咸鱼和反派错绑对方系统后") -> listOf(1) + (3..131)
            file.name.contains("星际第一种瓜王") -> (1..142).toList()
            file.name.contains("我在古代搞建设") -> (1..179).toList()
            file.name.contains("末世反派是我的工作搭子") -> (1..106).toList()
            file.name.contains("小猫咪在星际监狱") -> (1..66).toList()
            file.name.contains("这该死的修罗场") -> (1..77).toList()
            file.name.contains("这座仙宫叫医院") -> (1..357).toList()
            file.name.contains("半夜想吃前任做的红烧肉怎么办") -> (1..68).toList()
            else -> return
        }
        // This only extracts the known compact Arabic headings in this reviewed corpus.
        // Do not broaden it into a second chapter detector or treat every 第N回 as a title.
        val sourceHeadings = content.lineSequence()
            .map(String::trim)
            .mapNotNull(ReviewedSourceHeadingRegex::matchEntire)
            .toList()
        assertEquals(
            "${file.name}: source chapter sequence changed; review the source before updating expectations",
            expectedNumbers,
            sourceHeadings.map { it.groupValues[1].toInt() },
        )
        val expectedTitles = listOf("前言") + sourceHeadings.map { it.value } +
            if (file.name.contains("末世反派是我的工作搭子")) listOf("番外") else emptyList()
        assertEquals(
            "${file.name}: complete ordered chapter catalog; diagnostics=${parsed.diagnostics}",
            expectedTitles,
            parsed.chapters.map { it.title },
        )
    }

    private companion object {
        val ReviewedSourceHeadingRegex = Regex("^第([0-9]+)章(?:\\s+.*)?$")
    }
}
