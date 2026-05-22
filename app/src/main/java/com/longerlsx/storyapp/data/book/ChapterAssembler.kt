package com.longerlsx.storyapp.data.book

data class ChapterAssemblyResult(
    val chapters: List<ParsedChapter>,
    val fallbackMode: String?,
)

object ChapterAssembler {
    private const val LONG_FALLBACK_THRESHOLD = 20_000
    private const val FALLBACK_TARGET_SIZE = 12_000

    fun assemble(
        content: String,
        candidates: List<ChapterCandidate>,
    ): ChapterAssemblyResult {
        if (candidates.isEmpty()) {
            return fallback(content)
        }

        val chapters = mutableListOf<ParsedChapter>()
        val firstCandidate = candidates.first()
        addChapterIfNotBlank(
            chapters = chapters,
            title = "前言",
            content = content,
            rawStart = 0,
            rawEnd = firstCandidate.headingStartOffset,
        )

        candidates.forEachIndexed { index, candidate ->
            val nextHeadingStart = candidates.getOrNull(index + 1)?.headingStartOffset ?: content.length
            addChapterIfNotBlank(
                chapters = chapters,
                title = candidate.title,
                content = content,
                rawStart = candidate.bodyStartOffset,
                rawEnd = nextHeadingStart,
            )
        }

        return ChapterAssemblyResult(chapters, fallbackMode = null)
    }

    private fun fallback(content: String): ChapterAssemblyResult {
        val (start, end) = trimmedRange(content, 0, content.length)
        if (start >= end) {
            return ChapterAssemblyResult(emptyList(), fallbackMode = "empty")
        }
        if (end - start <= LONG_FALLBACK_THRESHOLD) {
            return ChapterAssemblyResult(
                chapters = listOf(
                    ParsedChapter(
                        title = "正文",
                        content = content.substring(start, end),
                        startOffset = start,
                        endOffset = end,
                    ),
                ),
                fallbackMode = "single",
            )
        }

        val chapters = mutableListOf<ParsedChapter>()
        var cursor = start
        var index = 1
        while (cursor < end) {
            val split = findFallbackSplit(content, cursor, end)
            val (chapterStart, chapterEnd) = trimmedRange(content, cursor, split)
            if (chapterStart < chapterEnd) {
                chapters += ParsedChapter(
                    title = "正文 $index",
                    content = content.substring(chapterStart, chapterEnd),
                    startOffset = chapterStart,
                    endOffset = chapterEnd,
                )
                index += 1
            }
            cursor = split.coerceAtLeast(cursor + 1)
        }

        return ChapterAssemblyResult(chapters, fallbackMode = "long")
    }

    private fun findFallbackSplit(content: String, start: Int, end: Int): Int {
        val desired = (start + FALLBACK_TARGET_SIZE).coerceAtMost(end)
        if (desired == end) {
            return end
        }

        val minimum = start + FALLBACK_TARGET_SIZE / 2
        val maximum = (start + FALLBACK_TARGET_SIZE + FALLBACK_TARGET_SIZE / 2).coerceAtMost(end)
        val before = content.lastIndexOf("\n\n", desired).takeIf { it >= minimum }
        if (before != null) {
            return before
        }
        val after = content.indexOf("\n\n", desired).takeIf { it != -1 && it <= maximum }
        if (after != null) {
            return after
        }
        val lineBreak = content.lastIndexOf('\n', desired).takeIf { it >= minimum }
        return lineBreak ?: desired
    }

    private fun addChapterIfNotBlank(
        chapters: MutableList<ParsedChapter>,
        title: String,
        content: String,
        rawStart: Int,
        rawEnd: Int,
    ) {
        val (start, end) = trimmedRange(content, rawStart, rawEnd)
        if (start >= end) {
            return
        }
        chapters += ParsedChapter(
            title = title,
            content = content.substring(start, end),
            startOffset = start,
            endOffset = end,
        )
    }

    private fun trimmedRange(
        content: String,
        rawStart: Int,
        rawEnd: Int,
    ): Pair<Int, Int> {
        var start = rawStart.coerceIn(0, content.length)
        var end = rawEnd.coerceIn(start, content.length)
        while (start < end && content[start].isWhitespace()) {
            start += 1
        }
        while (end > start && content[end - 1].isWhitespace()) {
            end -= 1
        }
        return start to end
    }
}
