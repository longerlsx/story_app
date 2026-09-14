package com.longerlsx.storyapp.data.book

data class ParsedChapter(
    val title: String,
    val content: String,
    val startOffset: Int,
    val endOffset: Int,
)

object ChapterParser {
    fun parse(content: String): List<ParsedChapter> {
        return parseDetailed(content).chapters
    }

    fun parseDetailed(content: String): ChapterParseResult {
        return parseNormalizedDetailed(TxtNormalizer.normalize(content))
    }

    /** Import offsets must refer to exactly the same normalized text used by disk readers. */
    internal fun parseNormalized(normalized: String): List<ParsedChapter> = parseNormalizedDetailed(normalized).chapters

    private fun parseNormalizedDetailed(normalized: String): ChapterParseResult {
        if (normalized.isBlank()) {
            return ChapterParseResult(
                chapters = emptyList(),
                diagnostics = ChapterParseDiagnostics(
                    selectedPrimaryRuleId = null,
                    auxiliaryRuleIds = emptyList(),
                    scannedCandidateCount = 0,
                    filteredCandidateCount = 0,
                    mergedCandidateCount = 0,
                    tocCandidatesDropped = 0,
                    doubleTitlesMerged = 0,
                    fallbackMode = "empty",
                ),
            )
        }

        val lines = ChapterLine.fromContent(normalized)
        val selection = ChapterRuleSelector.select(lines)
        val scanned = ChapterCandidateScanner.scan(lines, selection)
        val filterResult = ChapterCandidateFilter.filterDetailed(normalized, scanned)
        val mergeResult = ChapterCandidateMerger.mergeDetailed(normalized, filterResult.candidates)
        val assemblyResult = ChapterAssembler.assemble(normalized, mergeResult.candidates)

        return ChapterParseResult(
            chapters = assemblyResult.chapters,
            diagnostics = ChapterParseDiagnostics(
                selectedPrimaryRuleId = selection.primaryRuleId,
                auxiliaryRuleIds = selection.auxiliaryRules.map { it.id },
                scannedCandidateCount = scanned.size,
                filteredCandidateCount = filterResult.candidates.size,
                mergedCandidateCount = mergeResult.candidates.size,
                tocCandidatesDropped = filterResult.tocCandidatesDropped,
                doubleTitlesMerged = mergeResult.doubleTitlesMerged,
                fallbackMode = assemblyResult.fallbackMode,
            ),
        )
    }
}
