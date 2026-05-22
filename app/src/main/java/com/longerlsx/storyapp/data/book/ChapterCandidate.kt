package com.longerlsx.storyapp.data.book

data class ChapterCandidate(
    val title: String,
    val number: ChapterNumber?,
    val kind: ChapterTitleKind,
    val confidence: ChapterTitleConfidence,
    val ruleId: String,
    val lineIndex: Int,
    val headingStartOffset: Int,
    val headingEndOffset: Int,
    val bodyStartOffset: Int,
    val mergedTitles: List<String> = listOf(title),
)
