package com.longerlsx.storyapp.data.book

object ChapterCandidateScanner {
    fun scan(
        lines: List<ChapterLine>,
        selection: ChapterRuleSelection,
    ): List<ChapterCandidate> {
        val primaryRule = selection.primaryRule
        val compatibleChapterRules = if (primaryRule?.kind == ChapterTitleKind.CHAPTER) {
            ChapterDetectionRules.primaryRules.filter { rule ->
                rule.id != primaryRule.id && rule.kind == ChapterTitleKind.CHAPTER
            }
        } else {
            emptyList()
        }
        val auxiliaryRules = selection.auxiliaryRules

        return lines.mapNotNull { line ->
            primaryRule?.match(line)
                ?: compatibleChapterRules.firstNotNullOfOrNull { rule -> rule.match(line) }
                ?: auxiliaryRules.firstNotNullOfOrNull { rule -> rule.match(line) }
        }
    }
}
