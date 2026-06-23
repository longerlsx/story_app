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
        assertHeading("第十回 标题……", ChapterTitleKind.CHAPTER)
        assertHeading("第95章 ……", ChapterTitleKind.CHAPTER)
        assertHeading("第2章 独行者2:穿越者？玩家？神？", ChapterTitleKind.CHAPTER)
        assertHeading("第3章 标题：副题？", ChapterTitleKind.CHAPTER)
        assertHeading("第84章 幸福玛丽孤儿院1:“我愿意在你的胞宫中沉睡，直到世界的纱幕再一次掀起……”", ChapterTitleKind.CHAPTER)
        assertHeading("第1章 001 系统都能绑错？", ChapterTitleKind.CHAPTER)
        assertHeading("第51章 050 丹道比试，第一！", ChapterTitleKind.CHAPTER)
        assertHeading("第122章 121 嗯呐么哒。么么。", ChapterTitleKind.CHAPTER)
        assertHeading("第1章 ００１ 系统都能绑错？", ChapterTitleKind.CHAPTER)
        assertHeading("番外一", ChapterTitleKind.SPECIAL)
        assertHeading("后记", ChapterTitleKind.SPECIAL)
    }

    @Test
    fun rejectsBodyLinesThatLookSimilarToHeadings() {
        assertNull(match("第一章正文。"))
        assertNull(match("第1章 开始了。"))
        assertNull(match("第1章 1 开始了。"))
        assertNull(match("第1章 001开始了。"))
        assertNull(match("第十三回 ……"))
        assertNull(match("第十三回合：还要继续吗？"))
        assertNull(match("第1章 她想：开始了吗？"))
        assertNull(match("1. 这章：开始了吗？"))
        assertNull(match("这已经是第三回了。"))
        assertNull(match("第1章 ${"很长".repeat(30)}"))
        assertNull(match("1. ${"很".repeat(38)}"))
        assertNull(match("番外${"很".repeat(39)}"))
        assertNull(match("第一卷 ${"很".repeat(37)}"))
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
