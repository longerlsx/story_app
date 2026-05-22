package com.longerlsx.storyapp.data.book

data class ChapterRuleSelection(
    val primaryRule: ChapterDetectionRule?,
    val auxiliaryRules: List<ChapterDetectionRule>,
    val scores: Map<String, Int>,
) {
    val primaryRuleId: String? = primaryRule?.id
}

object ChapterRuleSelector {
    private const val SAMPLE_LINE_LIMIT = 2_000
    private const val SAMPLE_CHAR_LIMIT = 100_000

    fun select(lines: List<ChapterLine>): ChapterRuleSelection {
        val sample = frontSample(lines)
        val scores = ChapterDetectionRules.primaryRules.associate { rule ->
            rule.id to score(rule, sample)
        }
        val primary = ChapterDetectionRules.primaryRules
            .filter { rule -> scores.getValue(rule.id) > 0 }
            .maxWithOrNull(compareBy<ChapterDetectionRule> { scores.getValue(it.id) }.thenBy { it.priority })
        val auxiliaryRules = if (primary == null) {
            ChapterDetectionRules.defaultAuxiliaryRules + ChapterDetectionRules.structural
        } else {
            ChapterDetectionRules.defaultAuxiliaryRules
        }

        return ChapterRuleSelection(
            primaryRule = primary,
            auxiliaryRules = auxiliaryRules,
            scores = scores,
        )
    }

    private fun frontSample(lines: List<ChapterLine>): List<ChapterLine> {
        val sample = mutableListOf<ChapterLine>()
        for (line in lines) {
            if (sample.size >= SAMPLE_LINE_LIMIT || line.startOffset >= SAMPLE_CHAR_LIMIT) {
                break
            }
            sample += line
        }
        return sample
    }

    private fun score(rule: ChapterDetectionRule, lines: List<ChapterLine>): Int {
        val candidates = lines.mapNotNull(rule::match)
        if (candidates.isEmpty()) {
            return 0
        }

        val sequenceBonus = sequenceBonus(candidates)
        val distanceBonus = if (hasNonZeroDistance(candidates)) 20 else 0
        val openingClusterPenalty = openingClusterPenalty(candidates, lines)
        return candidates.size * 1_000 + sequenceBonus + distanceBonus + rule.priority - openingClusterPenalty
    }

    private fun sequenceBonus(candidates: List<ChapterCandidate>): Int {
        val values = candidates.mapNotNull { it.number?.value }
        if (values.isEmpty()) {
            return 0
        }
        val increasingPairs = values.zipWithNext().count { (left, right) -> right >= left }
        return increasingPairs * 100 + values.size * 10
    }

    private fun hasNonZeroDistance(candidates: List<ChapterCandidate>): Boolean {
        return candidates.zipWithNext().any { (left, right) ->
            right.headingStartOffset > left.bodyStartOffset
        }
    }

    private fun openingClusterPenalty(
        candidates: List<ChapterCandidate>,
        lines: List<ChapterLine>,
    ): Int {
        val clusteredPairs = candidates.zipWithNext().count { (left, right) ->
            left.lineIndex < 300 &&
                right.lineIndex < 300 &&
                !hasMeaningfulLineBetween(lines, left.lineIndex, right.lineIndex)
        }
        return clusteredPairs * 1_500
    }

    private fun hasMeaningfulLineBetween(
        lines: List<ChapterLine>,
        leftLineIndex: Int,
        rightLineIndex: Int,
    ): Boolean {
        return lines.any { line ->
            line.lineIndex in (leftLineIndex + 1) until rightLineIndex && line.trimmed.isNotBlank()
        }
    }
}
