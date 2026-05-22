package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterDetectionRuleTest {

    @Test
    fun matchesCommonChineseChapterHeadings() {
        assertHeading("第1章 标题", ChapterTitleKind.CHAPTER)
        assertHeading("第 1 章", ChapterTitleKind.CHAPTER)
        assertHeading("第　1　章", ChapterTitleKind.CHAPTER)
        assertHeading("第001章", ChapterTitleKind.CHAPTER)
        assertHeading("第一章", ChapterTitleKind.CHAPTER)
        assertHeading("第十回", ChapterTitleKind.CHAPTER)
        assertHeading("番外一", ChapterTitleKind.SPECIAL)
        assertHeading("后记", ChapterTitleKind.SPECIAL)
    }

    @Test
    fun rejectsBodyLinesThatLookSimilarToHeadings() {
        assertNull(match("第一章正文。"))
        assertNull(match("第1章 开始了。"))
        assertNull(match("这已经是第三回了。"))
        assertNull(match("第1章 ${"很长".repeat(30)}"))
    }

    private fun assertHeading(lineText: String, kind: ChapterTitleKind) {
        val match = match(lineText)
        assertNotNull(match)
        assertEquals(kind, match?.kind)
    }

    private fun match(lineText: String): ChapterCandidate? {
        val line = ChapterLine.fromContent(lineText).single()
        return ChapterDetectionRules.matchAll(line).firstOrNull()
    }
}
