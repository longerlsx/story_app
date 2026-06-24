package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterHeadingTextPolicyTest {

    @Test
    fun generalHeadingPolicyRejectsLongOrSentenceLikeLines() {
        assertTrue(ChapterHeadingTextPolicy.isLikelyHeading("番外一"))
        assertTrue(ChapterHeadingTextPolicy.isLikelyHeading("1. 初见"))

        assertFalse(ChapterHeadingTextPolicy.isLikelyHeading("第1章 开始了。"))
        assertFalse(ChapterHeadingTextPolicy.isLikelyHeading("1. 这章：开始了吗？"))
        assertFalse(ChapterHeadingTextPolicy.isLikelyHeading("番外${"很".repeat(39)}"))
    }

    @Test
    fun chineseNumberedPolicyAllowsObservedSubtitlePunctuationHeadings() {
        assertTrue(
            ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                suffix = "章",
                tail = " 独行者2:穿越者？玩家？神？",
                title = "第2章 独行者2:穿越者？玩家？神？",
            ),
        )
        assertTrue(
            ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                suffix = "章",
                tail = " 001 系统都能绑错？",
                title = "第1章 001 系统都能绑错？",
            ),
        )
        assertTrue(
            ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                suffix = "章",
                tail = " ……",
                title = "第95章 ……",
            ),
        )
    }

    @Test
    fun chineseNumberedPolicyRejectsBodyDialogueAndRoundMarkers() {
        assertFalse(
            ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                suffix = "章",
                tail = " 她想：开始了吗？",
                title = "第1章 她想：开始了吗？",
            ),
        )
        assertFalse(
            ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                suffix = "回",
                tail = "合：还要继续吗？",
                title = "第十三回合：还要继续吗？",
            ),
        )
        assertFalse(
            ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                suffix = "回",
                tail = " ……",
                title = "第十三回 ……",
            ),
        )
        assertFalse(
            ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                suffix = "章",
                tail = " 1 开始了。",
                title = "第1章 1 开始了。",
            ),
        )
    }
}
