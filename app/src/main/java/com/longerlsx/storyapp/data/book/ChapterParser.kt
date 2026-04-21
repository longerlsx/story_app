package com.longerlsx.storyapp.data.book

data class ParsedChapter(
    val title: String,
    val content: String,
    val startOffset: Int,
    val endOffset: Int,
)

object ChapterParser {
    fun parse(content: String): List<ParsedChapter> {
        val normalized = TxtNormalizer.normalize(content)
        if (normalized.isBlank()) {
            return emptyList()
        }

        val markers = findChapterMarkers(normalized)
        if (markers.isEmpty()) {
            return listOf(
                ParsedChapter(
                    title = "正文",
                    content = normalized.trim(),
                    startOffset = 0,
                    endOffset = normalized.length,
                ),
            )
        }

        val chapters = mutableListOf<ParsedChapter>()
        val firstMarker = markers.first()
        val preface = normalized.substring(0, firstMarker.offset).trim()
        if (preface.isNotBlank()) {
            chapters += ParsedChapter(
                title = "前言",
                content = preface,
                startOffset = 0,
                endOffset = firstMarker.offset,
            )
        }

        markers.forEachIndexed { index, marker ->
            val sectionEnd = markers.getOrNull(index + 1)?.offset ?: normalized.length
            val section = normalized.substring(marker.offset, sectionEnd).trim('\n')
            val body = section.lineSequence()
                .drop(1)
                .joinToString("\n")
                .trim()

            chapters += ParsedChapter(
                title = marker.title,
                content = body,
                startOffset = marker.offset,
                endOffset = sectionEnd,
            )
        }

        return chapters
    }

    private fun findChapterMarkers(content: String): List<ChapterMarker> {
        val markers = mutableListOf<ChapterMarker>()
        val lines = content.split('\n')
        var offset = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (ChapterPatterns.isChapterTitle(trimmed)) {
                markers += ChapterMarker(
                    title = trimmed,
                    offset = offset,
                )
            }

            offset += line.length
            if (offset < content.length && content[offset] == '\n') {
                offset += 1
            }
        }

        return markers
    }

    private data class ChapterMarker(
        val title: String,
        val offset: Int,
    )
}
