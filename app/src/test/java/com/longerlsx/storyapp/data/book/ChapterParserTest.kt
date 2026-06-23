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
    fun parserMergesRepeatedSpecialSubtitleAfterNumberedChapterTitle() {
        val content = """
            第141章 番外一
            　　番外一
            N年以后。

            第142章 番外二
            　　番外二
            谢钊很迅速地起了床。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("第141章 番外一", "第142章 番外二"), chapters.map { it.title })
        assertEquals("N年以后。", chapters[0].content)
        assertEquals("谢钊很迅速地起了床。", chapters[1].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserKeepsIndependentSpecialChapterAfterBodyText() {
        val content = """
            第1章 开始
            正文。

            番外一
            番外正文。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("第1章 开始", "番外一"), chapters.map { it.title })
        assertEquals("正文。", chapters[0].content)
        assertEquals("番外正文。", chapters[1].content)
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
    fun parserKeepsRoundAttemptEllipsisLinesInsideChapterBody() {
        val content = """
            第26章 击坠大天使之日2:他真的能击败如此可怕的敌人吗？
            正文前。

            第十三回 ……
            黎昭一次次尝试，一次次失败。

            第27章 下一章
            下一章正文。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(
            listOf(
                "第26章 击坠大天使之日2:他真的能击败如此可怕的敌人吗？",
                "第27章 下一章",
            ),
            chapters.map { it.title },
        )
        assertTrue(chapters[0].content.contains("第十三回 ……"))
        assertTrue(chapters[0].content.contains("黎昭一次次尝试，一次次失败。"))
        assertEquals("下一章正文。", chapters[1].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserKeepsRoundWordDialogueInsideChapterBody() {
        val content = """
            第12章 训练
            正文前。

            第十三回合：还要继续吗？
            黎昭又试了一次。

            第13章 结束
            下一章正文。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("第12章 训练", "第13章 结束"), chapters.map { it.title })
        assertTrue(chapters[0].content.contains("第十三回合：还要继续吗？"))
        assertTrue(chapters[0].content.contains("黎昭又试了一次。"))
        assertEquals("下一章正文。", chapters[1].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserKeepsChapterDialogueInsideChapterBody() {
        val content = """
            第1章 初见
            正文前。

            第1章 她想：开始了吗？
            这只是正文里的句子。

            第2章 重逢
            下一章正文。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(listOf("第1章 初见", "第2章 重逢"), chapters.map { it.title })
        assertTrue(chapters[0].content.contains("第1章 她想：开始了吗？"))
        assertTrue(chapters[0].content.contains("这只是正文里的句子。"))
        assertEquals("下一章正文。", chapters[1].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserKeepsRealSubtitlePunctuationChapterTitles() {
        val content = """
            第1章 独行者1:“杀人犯终于被抓住了！”
            正文一。

            第2章 独行者2:穿越者？玩家？神？
            正文二。

            第3章 标题：副题？
            正文三。

            第84章 幸福玛丽孤儿院1:“我愿意在你的胞宫中沉睡，直到世界的纱幕再一次掀起……”
            正文四。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(
            listOf(
                "第1章 独行者1:“杀人犯终于被抓住了！”",
                "第2章 独行者2:穿越者？玩家？神？",
                "第3章 标题：副题？",
                "第84章 幸福玛丽孤儿院1:“我愿意在你的胞宫中沉睡，直到世界的纱幕再一次掀起……”",
            ),
            chapters.map { it.title },
        )
        assertEquals("正文一。", chapters[0].content)
        assertEquals("正文二。", chapters[1].content)
        assertEquals("正文三。", chapters[2].content)
        assertEquals("正文四。", chapters[3].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserKeepsNumberedSubtitlePunctuationChapterTitles() {
        val content = """
            第1章 001 系统都能绑错？
            正文一。

            第51章 050 丹道比试，第一！
            正文二。

            第122章 121 嗯呐么哒。么么。
            正文三。

            第１章 ００１ 全角数字也能识别？
            正文四。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(
            listOf(
                "第1章 001 系统都能绑错？",
                "第51章 050 丹道比试，第一！",
                "第122章 121 嗯呐么哒。么么。",
                "第１章 ００１ 全角数字也能识别？",
            ),
            chapters.map { it.title },
        )
        assertEquals("正文一。", chapters[0].content)
        assertEquals("正文二。", chapters[1].content)
        assertEquals("正文三。", chapters[2].content)
        assertEquals("正文四。", chapters[3].content)
        assertOffsetsExactAndMonotonic(content, chapters)
    }

    @Test
    fun parserPreservesRepresentativeExternalCorpusOffsetsWithoutLocalCorpus() {
        val content = """
            第2章 独行者2:穿越者？玩家？神？
            玩家指南正文。

            第84章 幸福玛丽孤儿院1:“我愿意在你的胞宫中沉睡，直到世界的纱幕再一次掀起……”
            孤儿院正文。

            第141章 番外一
            　　番外一
            番外正文。

            第95章 ……
            省略标题正文。
        """.trimIndent()

        val chapters = ChapterParser.parse(content)

        assertEquals(
            listOf(
                "第2章 独行者2:穿越者？玩家？神？",
                "第84章 幸福玛丽孤儿院1:“我愿意在你的胞宫中沉睡，直到世界的纱幕再一次掀起……”",
                "第141章 番外一",
                "第95章 ……",
            ),
            chapters.map { it.title },
        )
        assertEquals("玩家指南正文。", chapters[0].content)
        assertEquals("孤儿院正文。", chapters[1].content)
        assertEquals("番外正文。", chapters[2].content)
        assertEquals("省略标题正文。", chapters[3].content)
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
