package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserTest {

    @Test
    fun parserPreservesPrefaceBeforeFirstChapter() {
        val content = loadFixture("fixtures/sample_book_chapters.txt")

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("前言", "第1章 初到监狱", "第一章 训练开始"), chapters.map { it.title })
        assertTrue(chapters.first().content.contains("这是前言第二段。"))
        assertTrue(chapters[1].content.contains("小猫咪睁开眼睛"))
        assertTrue(chapters[2].content.contains("这里的一切都不简单"))
    }

    @Test
    fun parserKeepsBodyForLaterNumericChapterTitles() {
        val content = """
            《目录测试》
            作者：测试作者

            第1章 开始
            第一章正文。

            第2章 继续
            第二章正文。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("前言", "第1章 开始", "第2章 继续"), chapters.map { it.title })
        assertEquals("第二章正文。", chapters[2].content)
    }

    @Test
    fun parserMergesSpacedNumberDoubleTitles() {
        val content = loadFixture("fixtures/txt_double_title_spaced_number.txt")

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("前言", "第 1 章", "第 2 章"), chapters.map { it.title })
        assertEquals("正文第一段。", chapters[1].content)
        assertFalse(chapters[1].content.contains("第一章"))
        assertEquals("正文第二段。", chapters[2].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserDropsFrontTocHeadingsWhenBodyHeadingsRepeat() {
        val content = loadFixture("fixtures/txt_front_toc_then_body.txt")

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("前言", "第1章 初见", "第2章 重逢"), chapters.map { it.title })
        assertEquals("正文一。", chapters[1].content)
        assertEquals("正文二。", chapters[2].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserDropsNumericTocWhenBodyUsesChineseChapterHeadings() {
        val toc = (1..12).joinToString("\n") { "$it. 目录$it" }
        val content = """
            目录
            $toc

            第1章 初见
            正文一。

            第2章 重逢
            正文二。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("前言", "第1章 初见", "第2章 重逢"), chapters.map { it.title })
        assertEquals("正文一。", chapters[1].content)
        assertEquals("正文二。", chapters[2].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserDoesNotSplitBodyLinesThatOnlyLookLikeHeadings() {
        val content = """
            第1章 开始
            第一章正文。
            第1章 开始了。
            这已经是第三回了。

            第2章 继续
            第二章正文。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("第1章 开始", "第2章 继续"), chapters.map { it.title })
        assertTrue(chapters[0].content.contains("第一章正文。"))
        assertTrue(chapters[0].content.contains("第1章 开始了。"))
        assertTrue(chapters[0].content.contains("这已经是第三回了。"))
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserDoesNotCreateEmptyChapterForVolumeHeading() {
        val content = loadFixture("fixtures/txt_volume_then_chapter.txt")

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("前言", "第一章 初见", "第二章 重逢"), chapters.map { it.title })
        assertTrue(chapters.none { it.title.contains("第一卷") })
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserKeepsSingleShortChapterEvenWithOneCandidate() {
        val content = """
            第1章 唯一
            很短的正文。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("第1章 唯一"), chapters.map { it.title })
        assertEquals("很短的正文。", chapters.single().content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserUsesStructuralMarkersWhenNoNumberedRuleExists() {
        val content = """
            前言
            简介内容。

            正文
            正文内容。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("前言", "正文"), chapters.map { it.title })
        assertEquals("简介内容。", chapters[0].content)
        assertEquals("正文内容。", chapters[1].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserKeepsShortNoTitleTextAsSingleBodyChapter() {
        val content = "没有标题的短篇正文。\n第二段。"

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("正文"), chapters.map { it.title })
        assertEquals(content, chapters.single().content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserSupportsNumericTitleChaptersEndToEnd() {
        val content = """
            1. 初见
            正文一。

            2. 重逢
            正文二。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("1. 初见", "2. 重逢"), chapters.map { it.title })
        assertEquals("正文一。", chapters[0].content)
        assertEquals("正文二。", chapters[1].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserOffsetsPointExactlyToReturnedContent() {
        val content = loadFixture("fixtures/txt_double_title_spaced_number.txt")
        val normalized = TxtNormalizer.normalize(content)

        val chapters = ChapterParser.parse(content)

        chapters.forEach { chapter ->
            assertEquals(chapter.content, normalized.substring(chapter.startOffset, chapter.endOffset))
        }
    }

    @Test
    fun parserSplitsLongNoTitleTextOnParagraphBoundaries() {
        val paragraph = loadFixture("fixtures/txt_no_title_long.txt").trim()
        val content = (1..900).joinToString("\n\n") { "$paragraph $it" }

        val chapters = ChapterParser.parse(content)

        assertTrue(chapters.size > 1)
        assertTrue(chapters.all { it.title.startsWith("正文") })
        assertTrue(chapters.all { it.content.isNotBlank() })
        assertFalse(chapters.any { it.content.startsWith("\n") || it.content.endsWith("\n") })
        assertTrue(chapters.joinToString("\n\n") { it.content }.contains("$paragraph 900"))
        assertEquals(content, chapters.joinToString("\n\n") { it.content })
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parseDetailedReportsDiagnosticsForFilteringMergingAndFallback() {
        val doubleTitle = ChapterParser.parseDetailed(loadFixture("fixtures/txt_double_title_spaced_number.txt"))
        assertEquals("chinese-numbered-spaced", doubleTitle.diagnostics.selectedPrimaryRuleId)
        assertEquals(2, doubleTitle.diagnostics.doubleTitlesMerged)

        val toc = ChapterParser.parseDetailed(loadFixture("fixtures/txt_front_toc_then_body.txt"))
        assertTrue(toc.diagnostics.tocCandidatesDropped > 0)

        val fallback = ChapterParser.parseDetailed("没有标题的正文。")
        assertEquals("single", fallback.diagnostics.fallbackMode)
    }

    private fun loadFixture(path: String): String {
        return requireNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing fixture: $path"
        }.readText()
    }

    private fun assertOffsetsExactAndMonotonic(content: String, chapters: List<ParsedChapter>) {
        val normalized = TxtNormalizer.normalize(content)
        var previousEnd = 0
        chapters.forEach { chapter ->
            assertTrue(chapter.startOffset in 0..normalized.length)
            assertTrue(chapter.endOffset in chapter.startOffset..normalized.length)
            assertTrue(chapter.startOffset >= previousEnd)
            assertEquals(chapter.content, normalized.substring(chapter.startOffset, chapter.endOffset))
            previousEnd = chapter.endOffset
        }
    }
}
