package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
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

    private fun loadFixture(path: String): String {
        return requireNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing fixture: $path"
        }.readText()
    }
}
