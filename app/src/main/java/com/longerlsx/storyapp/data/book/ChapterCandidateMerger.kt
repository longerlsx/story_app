package com.longerlsx.storyapp.data.book

data class ChapterCandidateMergeResult(
    val candidates: List<ChapterCandidate>,
    val doubleTitlesMerged: Int,
)

object ChapterCandidateMerger {
    fun merge(content: String, candidates: List<ChapterCandidate>): List<ChapterCandidate> {
        return mergeDetailed(content, candidates).candidates
    }

    fun mergeDetailed(content: String, candidates: List<ChapterCandidate>): ChapterCandidateMergeResult {
        if (candidates.isEmpty()) {
            return ChapterCandidateMergeResult(emptyList(), doubleTitlesMerged = 0)
        }

        val merged = mutableListOf<ChapterCandidate>()
        var index = 0
        var mergeCount = 0
        while (index < candidates.size) {
            var current = candidates[index]
            var nextIndex = index + 1
            while (nextIndex < candidates.size && canMerge(content, current, candidates[nextIndex])) {
                val next = candidates[nextIndex]
                current = current.copy(
                    bodyStartOffset = next.bodyStartOffset,
                    mergedTitles = current.mergedTitles + next.title,
                )
                mergeCount += 1
                nextIndex += 1
            }
            merged += current
            index = nextIndex
        }

        return ChapterCandidateMergeResult(merged, mergeCount)
    }

    private fun canMerge(
        content: String,
        current: ChapterCandidate,
        next: ChapterCandidate,
    ): Boolean {
        if (next.lineIndex - current.lineIndex > 2) {
            return false
        }
        if (ChapterCandidateFilter.hasMeaningfulText(content, current.bodyStartOffset, next.headingStartOffset)) {
            return false
        }
        if (current.kind != ChapterTitleKind.CHAPTER || next.kind != ChapterTitleKind.CHAPTER) {
            return false
        }

        val currentNumber = current.number?.value
        val nextNumber = next.number?.value
        return currentNumber != null && currentNumber == nextNumber ||
            normalizeTitle(current.title) == normalizeTitle(next.title)
    }

    private fun normalizeTitle(title: String): String {
        return title.filterNot(Char::isWhitespace)
    }
}
