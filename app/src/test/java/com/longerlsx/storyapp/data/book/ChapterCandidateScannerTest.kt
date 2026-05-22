package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterCandidateScannerTest {

    @Test
    fun scannerFindsHeadingsBeyondFrontSample() {
        val longMiddle = (1..2_100).joinToString("\n") { "正文行$it" }
        val content = """
            第1章 开始
            正文。
            $longMiddle

            第2章 后段
            后段正文。
        """.trimIndent()

        val candidates = scan(content)

        assertEquals(listOf("第1章 开始", "第2章 后段"), candidates.map { it.title })
    }

    @Test
    fun scannerFindsHeadingsBeyondFrontCharacterSample() {
        val longLine = "正文".repeat(60_000)
        val content = """
            第1章 开始
            $longLine

            第2章 后段
            后段正文。
        """.trimIndent()

        val candidates = scan(content)

        assertEquals(listOf("第1章 开始", "第2章 后段"), candidates.map { it.title })
    }

    @Test
    fun scannerIncludesAuxiliarySpecialMarkers() {
        val candidates = scan(
            """
            第1章 开始
            正文。

            番外一
            番外正文。

            后记
            后记正文。
            """.trimIndent(),
        )

        assertTrue(candidates.any { it.title == "番外一" })
        assertTrue(candidates.any { it.title == "后记" })
    }

    @Test
    fun scannerDoesNotApplyUnselectedRulesToBodyLines() {
        val candidates = scan(
            """
            第1章 开始
            正文。
            1. 这不是标题。

            第2章 继续
            正文。
            """.trimIndent(),
        )

        assertFalse(candidates.any { it.title.startsWith("1.") })
    }

    @Test
    fun scannerDoesNotTreatCompactBodyLikeLineAsCompatibleDoubleTitle() {
        val candidates = scan(
            """
            第 1 章
            第一章
            正文。
            第一章正文。

            第 2 章
            第二章
            正文。
            """.trimIndent(),
        )

        assertFalse(candidates.any { it.title == "第一章正文。" })
    }

    @Test
    fun scannerDoesNotApplyChineseChapterRulesWhenNumericRuleIsPrimary() {
        val content = """
            1. 初见
            正文。
            第1章 回忆
            这只是正文里的回忆标题。

            2. 重逢
            正文。
        """.trimIndent()

        val candidates = scan(content)

        assertEquals(listOf("1. 初见", "2. 重逢"), candidates.map { it.title })
    }

    @Test
    fun scannerReturnsSingleShortChapterCandidate() {
        val candidates = scan(
            """
            第1章 唯一
            正文。
            """.trimIndent(),
        )

        assertEquals(listOf("第1章 唯一"), candidates.map { it.title })
    }

    private fun scan(content: String): List<ChapterCandidate> {
        val lines = ChapterLine.fromContent(content)
        val selection = ChapterRuleSelector.select(lines)
        return ChapterCandidateScanner.scan(lines, selection)
    }
}
