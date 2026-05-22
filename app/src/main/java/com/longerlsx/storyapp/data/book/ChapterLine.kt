package com.longerlsx.storyapp.data.book

data class ChapterLine(
    val lineIndex: Int,
    val text: String,
    val trimmed: String,
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val nextLineStartOffset: Int,
    val trimmedStartOffset: Int,
    val trimmedEndOffsetExclusive: Int,
) {
    companion object {
        fun fromContent(content: String): List<ChapterLine> {
            if (content.isEmpty()) {
                return emptyList()
            }

            val lines = mutableListOf<ChapterLine>()
            var lineStart = 0
            var lineIndex = 0
            while (lineStart < content.length) {
                val newlineIndex = content.indexOf('\n', lineStart)
                val lineEnd = if (newlineIndex == -1) content.length else newlineIndex
                val nextLineStart = if (newlineIndex == -1) lineEnd else newlineIndex + 1
                val text = content.substring(lineStart, lineEnd)
                val trimStart = text.indexOfFirst { !it.isWhitespace() }.let { index ->
                    if (index == -1) text.length else index
                }
                val trimEnd = text.indexOfLast { !it.isWhitespace() }.let { index ->
                    if (index == -1) trimStart else index + 1
                }

                lines += ChapterLine(
                    lineIndex = lineIndex,
                    text = text,
                    trimmed = text.substring(trimStart, trimEnd),
                    startOffset = lineStart,
                    endOffsetExclusive = lineEnd,
                    nextLineStartOffset = nextLineStart,
                    trimmedStartOffset = lineStart + trimStart,
                    trimmedEndOffsetExclusive = lineStart + trimEnd,
                )

                if (newlineIndex == -1) {
                    break
                }
                lineStart = nextLineStart
                lineIndex += 1
            }

            return lines
        }
    }
}
