package com.longerlsx.storyapp.data.book

data class ChapterCandidateFilterResult(
    val candidates: List<ChapterCandidate>,
    val tocCandidatesDropped: Int,
)

object ChapterCandidateFilter {
    fun filter(content: String, candidates: List<ChapterCandidate>): List<ChapterCandidate> {
        return filterDetailed(content, candidates).candidates
    }

    fun filterDetailed(content: String, candidates: List<ChapterCandidate>): ChapterCandidateFilterResult {
        if (candidates.isEmpty()) {
            return ChapterCandidateFilterResult(emptyList(), tocCandidatesDropped = 0)
        }

        val withoutVolumes = candidates.filterIndexed { index, candidate ->
            !isDiscardableVolume(content, candidates, index, candidate)
        }
        var dropped = 0
        val withoutToc = withoutVolumes.filterIndexed { index, candidate ->
            val drop = isOpeningTocDuplicate(content, withoutVolumes, index, candidate)
            if (drop) {
                dropped += 1
            }
            !drop
        }

        return ChapterCandidateFilterResult(withoutToc, tocCandidatesDropped = dropped)
    }

    private fun isDiscardableVolume(
        content: String,
        candidates: List<ChapterCandidate>,
        index: Int,
        candidate: ChapterCandidate,
    ): Boolean {
        if (candidate.kind != ChapterTitleKind.VOLUME) {
            return false
        }
        val next = candidates.getOrNull(index + 1) ?: return false
        return next.lineIndex - candidate.lineIndex <= 2 &&
            !hasMeaningfulText(content, candidate.bodyStartOffset, next.headingStartOffset)
    }

    private fun isOpeningTocDuplicate(
        content: String,
        candidates: List<ChapterCandidate>,
        index: Int,
        candidate: ChapterCandidate,
    ): Boolean {
        val number = candidate.number?.value ?: return false
        if (candidate.lineIndex > 300) {
            return false
        }
        val next = candidates.getOrNull(index + 1) ?: return false
        if (next.number?.value == number && next.lineIndex - candidate.lineIndex <= 2) {
            return false
        }
        if (hasMeaningfulText(content, candidate.bodyStartOffset, next.headingStartOffset)) {
            return false
        }

        return candidates.drop(index + 1).any { later -> later.number?.value == number }
    }

    internal fun hasMeaningfulText(content: String, start: Int, end: Int): Boolean {
        val safeStart = start.coerceIn(0, content.length)
        val safeEnd = end.coerceIn(safeStart, content.length)
        return content.substring(safeStart, safeEnd).any { !it.isWhitespace() }
    }
}
