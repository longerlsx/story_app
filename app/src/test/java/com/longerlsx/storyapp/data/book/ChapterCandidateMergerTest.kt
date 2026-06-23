package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterCandidateMergerTest {

    @Test
    fun mergesAdjacentEquivalentDoubleTitles() {
        val content = """
            第 1 章
            第一章
            正文。

            第 2 章
            第二章
            正文。
        """.trimIndent()

        val merged = mergedCandidates(content)

        assertEquals(listOf("第 1 章", "第 2 章"), merged.map { it.title })
        assertEquals(content.indexOf("正文。"), merged.first().bodyStartOffset)
    }

    @Test
    fun mergesDoubleTitlesSeparatedByOneBlankLine() {
        val content = """
            第 1 章

            第一章
            正文。
        """.trimIndent()

        val merged = mergedCandidates(content)

        assertEquals(listOf("第 1 章"), merged.map { it.title })
        assertEquals(content.indexOf("正文。"), merged.first().bodyStartOffset)
    }

    @Test
    fun mergesChapterTitleFollowedByRepeatedSpecialSubtitle() {
        val content = """
            第141章 番外一
            　　番外一
            正文。
        """.trimIndent()

        val merged = mergedCandidates(content)

        assertEquals(listOf("第141章 番外一"), merged.map { it.title })
        assertEquals(content.indexOf("正文。"), merged.first().bodyStartOffset)
    }

    @Test
    fun doesNotMergeDifferentShortChaptersOnlyBecauseTheyAreClose() {
        val content = """
            第1章
            一。
            第2章
            二。
        """.trimIndent()

        val merged = mergedCandidates(content)

        assertEquals(listOf("第1章", "第2章"), merged.map { it.title })
    }

    @Test
    fun doesNotMergeIndependentSpecialChapterAfterBodyText() {
        val content = """
            第1章 开始
            正文。

            番外一
            番外正文。
        """.trimIndent()

        val merged = mergedCandidates(content)

        assertEquals(listOf("第1章 开始", "番外一"), merged.map { it.title })
    }

    @Test
    fun doesNotMergeAdjacentSpecialSubtitleUnlessTailMatchesExactly() {
        val content = """
            第141章 番外一（上）
            　　番外一
            正文。
        """.trimIndent()

        val merged = mergedCandidates(content)

        assertEquals(listOf("第141章 番外一（上）", "番外一"), merged.map { it.title })
    }

    private fun mergedCandidates(content: String): List<ChapterCandidate> {
        val lines = ChapterLine.fromContent(content)
        val selection = ChapterRuleSelector.select(lines)
        val scanned = ChapterCandidateScanner.scan(lines, selection)
        val filtered = ChapterCandidateFilter.filter(content, scanned)
        return ChapterCandidateMerger.merge(content, filtered)
    }
}
