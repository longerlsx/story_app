package com.longerlsx.storyapp.feature.reader.tts

object ReaderTtsSegmenter {
    private const val DEFAULT_MAX_CHUNK_CHARS = 180

    fun segment(
        chapterIndex: Int,
        text: String,
        maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
    ): List<ReaderTtsSegment> {
        if (text.isBlank()) {
            return emptyList()
        }

        val sentenceSpans = splitIntoSentenceSpans(text)
        return packSpans(chapterIndex, sentenceSpans, maxChunkChars)
    }

    private fun splitIntoSentenceSpans(text: String): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()
        var cursor = 0

        while (cursor < text.length) {
            while (cursor < text.length && text[cursor].isWhitespace()) {
                cursor++
            }
            if (cursor >= text.length) {
                break
            }

            val start = cursor
            var end = cursor
            var foundTerminator = false

            while (end < text.length) {
                val ch = text[end]
                end++
                if (isSentenceTerminator(ch)) {
                    foundTerminator = true
                    while (end < text.length && isSentenceTerminator(text[end])) {
                        end++
                    }
                    break
                }
            }

            if (!foundTerminator) {
                end = text.length
            }

            while (end > start && text[end - 1].isWhitespace()) {
                end--
            }

            spans += TextSpan(start = start, end = end, text = text.substring(start, end))
            cursor = end
        }

        return spans
    }

    private fun packSpans(
        chapterIndex: Int,
        spans: List<TextSpan>,
        maxChunkChars: Int,
    ): List<ReaderTtsSegment> {
        val segments = mutableListOf<ReaderTtsSegment>()
        var buffer = mutableListOf<TextSpan>()
        var bufferLength = 0

        fun flushBuffer() {
            if (buffer.isEmpty()) {
                return
            }

            segments += ReaderTtsSegment(
                chapterIndex = chapterIndex,
                startCharOffset = buffer.first().start,
                endCharOffset = buffer.last().end,
                spokenText = sanitizeForSpeech(buffer.joinToString(" ") { it.text }),
            )
            buffer = mutableListOf()
            bufferLength = 0
        }

        for (span in spans) {
            val projectedLength = if (buffer.isEmpty()) {
                span.text.length
            } else {
                bufferLength + 1 + span.text.length
            }

            if (buffer.isNotEmpty() && projectedLength > maxChunkChars) {
                flushBuffer()
            }

            buffer += span
            bufferLength = if (buffer.size == 1) span.text.length else bufferLength + 1 + span.text.length
        }

        flushBuffer()
        return segments
    }

    private fun isSentenceTerminator(ch: Char): Boolean {
        return ch == '.' || ch == '!' || ch == '?' || ch == '。' || ch == '！' || ch == '？'
    }

    fun sanitizeForSpeech(text: String): String {
        return text
            .map { ch -> if (isNoisySymbol(ch)) ' ' else ch }
            .joinToString(separator = "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isNoisySymbol(ch: Char): Boolean {
        return ch == '-' ||
            ch == '－' ||
            ch == '—' ||
            ch == '–' ||
            ch == '*' ||
            ch == '＊' ||
            ch == '【' ||
            ch == '】' ||
            ch == '[' ||
            ch == ']'
    }

    private data class TextSpan(
        val start: Int,
        val end: Int,
        val text: String,
    )
}
