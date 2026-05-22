package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterRuleSelectorTest {

    @Test
    fun selectsCompactChineseNumberedRuleForNormalBooks() {
        val selection = select(
            """
            第1章 开始
            正文。

            第2章 继续
            正文。
            """.trimIndent(),
        )

        assertEquals("chinese-numbered-compact", selection.primaryRuleId)
    }

    @Test
    fun selectsSpacedChineseNumberedRuleForSpacedBooks() {
        val selection = select(
            """
            第 1 章
            第一章
            正文。

            第 2 章
            第二章
            正文。
            """.trimIndent(),
        )

        assertEquals("chinese-numbered-spaced", selection.primaryRuleId)
    }

    @Test
    fun frontTocDoesNotSelectStructuralRuleOverNumberedRule() {
        val selection = select(loadFixture("fixtures/txt_front_toc_then_body.txt"))

        assertEquals("chinese-numbered-compact", selection.primaryRuleId)
        assertNotEquals("structural-marker", selection.primaryRuleId)
    }

    @Test
    fun oneChapterBookStillSelectsUsableRule() {
        val selection = select(
            """
            第1章 唯一
            正文。
            """.trimIndent(),
        )

        assertNotNull(selection.primaryRule)
    }

    @Test
    fun structuralMarkerAloneDoesNotBecomeHighConfidenceRule() {
        val selection = select(
            """
            正文
            这只是正文内容。
            """.trimIndent(),
        )

        assertNull(selection.primaryRule)
    }

    private fun select(content: String): ChapterRuleSelection {
        return ChapterRuleSelector.select(ChapterLine.fromContent(content))
    }

    private fun loadFixture(path: String): String {
        return requireNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing fixture: $path"
        }.readText()
    }
}
