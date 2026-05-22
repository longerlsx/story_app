package com.longerlsx.storyapp.data.book

data class ChapterParseResult(
    val chapters: List<ParsedChapter>,
    val diagnostics: ChapterParseDiagnostics,
)

data class ChapterParseDiagnostics(
    val selectedPrimaryRuleId: String?,
    val auxiliaryRuleIds: List<String>,
    val scannedCandidateCount: Int,
    val filteredCandidateCount: Int,
    val mergedCandidateCount: Int,
    val tocCandidatesDropped: Int,
    val doubleTitlesMerged: Int,
    val fallbackMode: String?,
)
