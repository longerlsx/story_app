package com.longerlsx.storyapp.feature.reader.tts

object ReaderTtsSegmenter {
    private const val DEFAULT_MAX_CHUNK_CHARS = 80

    fun segment(
        chapterIndex: Int,
        text: String,
        maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
        startCharOffset: Int = 0,
    ): List<ReaderTtsSegment> {
        require(maxChunkChars > 0) { "朗读分段长度必须大于零" }
        val segments = mutableListOf<ReaderTtsSegment>()
        var cursor = startCharOffset.coerceIn(0, text.length)
        if (cursor > 0 && cursor < text.length &&
            Character.isLowSurrogate(text[cursor]) && Character.isHighSurrogate(text[cursor - 1])
        ) {
            cursor--
        }
        while (cursor < text.length) {
            while (cursor < text.length && text[cursor].isWhitespace()) cursor++
            if (cursor == text.length) break

            // The bound counts complete Unicode characters; chapter offsets remain UTF-16.
            var end = cursor
            var characters = 0
            while (end < text.length && characters < maxChunkChars) {
                end += Character.charCount(text.codePointAt(end))
                characters++
            }
            if (end < text.length) end = preferredBoundary(text, cursor, end)
            while (end > cursor && text[end - 1].isWhitespace()) end--
            val normalized = normalizeForSpeech(text, cursor, end)
            if (normalized.text.isNotEmpty()) {
                segments += ReaderTtsSegment(
                    chapterIndex = chapterIndex,
                    startCharOffset = cursor,
                    endCharOffset = end,
                    spokenText = normalized.text,
                    spokenCharSourceRanges = normalized.sourceRanges,
                )
            }
            cursor = end
        }
        return segments
    }

    private fun preferredBoundary(text: String, start: Int, limit: Int): Int {
        var punctuationBoundary = -1
        var whitespaceBoundary = -1
        for (index in start until limit) {
            val character = text[index]
            if (character.isWhitespace()) whitespaceBoundary = index
            if (character in "。！？!?，,；;：:、.\n" && !isNumberSeparator(text, index)) {
                punctuationBoundary = index + 1
            }
        }
        if (punctuationBoundary > start) return punctuationBoundary
        if (whitespaceBoundary > start) return whitespaceBoundary

        // Keep a number/Latin word together when moving it still permits a bounded chunk.
        if (isTokenCharacter(text, limit - 1) && isTokenCharacter(text, limit)) {
            var tokenStart = limit
            while (tokenStart > start && isTokenCharacter(text, tokenStart - 1)) tokenStart--
            if (tokenStart > start) return tokenStart
        }
        return limit
    }

    private fun isTokenCharacter(text: String, index: Int): Boolean {
        return text[index].isDigit() || text[index] in 'a'..'z' || text[index] in 'A'..'Z' ||
            isNumberSeparator(text, index)
    }

    private fun isNumberSeparator(text: String, index: Int): Boolean {
        return text[index] in ".,:" && index > 0 && index + 1 < text.length &&
            text[index - 1].isDigit() && text[index + 1].isDigit()
    }

    fun sanitizeForSpeech(text: String): String {
        return normalizeForSpeech(text, 0, text.length).text
    }

    private fun normalizeForSpeech(text: String, start: Int, end: Int): NormalizedSpeech {
        val spoken = StringBuilder()
        val sourceRanges = mutableListOf<ReaderTtsCharacterRange>()
        var cursor = start
        var pendingSpaceStart = -1
        while (cursor < end) {
            val next = cursor + Character.charCount(text.codePointAt(cursor))
            if (text[cursor].isWhitespace() || isNoisySymbol(text[cursor])) {
                if (pendingSpaceStart == -1) pendingSpaceStart = cursor
            } else {
                if (pendingSpaceStart != -1 && spoken.isNotEmpty()) {
                    spoken.append(' ')
                    sourceRanges += ReaderTtsCharacterRange(pendingSpaceStart, cursor)
                }
                pendingSpaceStart = -1
                spoken.append(text, cursor, next)
                // Each UTF-16 half of a supplementary character maps to the whole source character.
                repeat(next - cursor) {
                    sourceRanges += ReaderTtsCharacterRange(cursor, next)
                }
            }
            cursor = next
        }
        return NormalizedSpeech(spoken.toString(), sourceRanges)
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

    private data class NormalizedSpeech(
        val text: String,
        val sourceRanges: List<ReaderTtsCharacterRange>,
    )
}
