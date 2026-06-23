package com.longerlsx.storyapp.feature.reader

internal data class ReaderDisplayParagraph(
    val text: String,
    val startCharOffset: Int,
    val endCharOffset: Int,
)

internal object ReaderParagraphModel {
    fun toDisplayParagraphs(text: String): List<ReaderDisplayParagraph> {
        if (text.isBlank()) {
            return listOf(emptyPlaceholder())
        }

        val paragraphs = mutableListOf<ReaderDisplayParagraph>()
        var lineStart = 0
        while (lineStart <= text.lastIndex) {
            val rawLineEnd = text.indexOf('\n', startIndex = lineStart)
                .takeIf { it >= 0 }
                ?: text.length
            val rawLine = text.substring(lineStart, rawLineEnd)
            val trimmedStart = rawLine.indexOfFirst { !it.isWhitespace() }
            val trimmedEnd = rawLine.indexOfLast { !it.isWhitespace() }
            if (trimmedStart >= 0 && trimmedEnd >= trimmedStart) {
                val startOffset = lineStart + trimmedStart
                val endOffset = lineStart + trimmedEnd + 1
                paragraphs += ReaderDisplayParagraph(
                    text = text.substring(startOffset, endOffset),
                    startCharOffset = startOffset,
                    endCharOffset = endOffset,
                )
            }
            if (rawLineEnd >= text.length) {
                break
            }
            lineStart = rawLineEnd + 1
        }
        return paragraphs.ifEmpty { listOf(emptyPlaceholder()) }
    }

    private fun emptyPlaceholder(): ReaderDisplayParagraph {
        return ReaderDisplayParagraph(
            text = "当前章节暂无正文。",
            startCharOffset = 0,
            endCharOffset = 0,
        )
    }
}
