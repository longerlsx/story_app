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
        val speechText = ReaderTtsLexicalTokens.foldWidth(text)
        val tokens = ReaderTtsLexicalTokens.scan(speechText)
        var tokenIndex = 0
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
            while (tokenIndex < tokens.size && tokens[tokenIndex].end <= cursor) tokenIndex++
            var tokenEnd = tokenIndex
            while (tokenEnd < tokens.size && tokens[tokenEnd].start < end) tokenEnd++
            val nearbyTokens = tokens.subList(tokenIndex, tokenEnd)
            if (end < text.length) end = preferredBoundary(speechText, cursor, end, maxChunkChars, nearbyTokens)
            while (end > cursor && text[end - 1].isWhitespace()) end--
            val normalized = normalizeForSpeech(speechText, cursor, end, nearbyTokens)
            if (normalized.text.isNotEmpty()) {
                segments += ReaderTtsSegment(
                    chapterIndex = chapterIndex,
                    startCharOffset = cursor,
                    endCharOffset = end,
                    spokenText = normalized.text,
                    spokenCharSourceRanges = normalized.sourceRanges,
                    numberReadingHints = normalized.numberReadingHints,
                )
            }
            cursor = end
        }
        return segments
    }

    private fun preferredBoundary(
        text: String,
        start: Int,
        limit: Int,
        maxChunkChars: Int,
        tokens: List<ReaderTtsLexicalTokens.Token>,
    ): Int {
        var punctuationBoundary = -1
        var whitespaceBoundary = -1
        for (index in start until limit) {
            val character = text[index]
            if (character.isWhitespace()) whitespaceBoundary = index
            if (character in "。！？!?，,；;：:、.\n" && tokens.none { index in it.start until it.end }) {
                punctuationBoundary = index + 1
            }
        }
        if (punctuationBoundary > start) return punctuationBoundary
        if (whitespaceBoundary > start) return whitespaceBoundary

        // Oversized compounds remain bounded and receive reading hints for their visible fragments.
        val crossing = tokens.firstOrNull { it.start < limit && it.end > limit }
        if (crossing != null && crossing.start > start && crossing.end - crossing.start <= maxChunkChars) {
            return crossing.start
        }
        return limit
    }

    fun sanitizeForSpeech(text: String): String {
        val folded = ReaderTtsLexicalTokens.foldWidth(text)
        return normalizeForSpeech(folded, 0, folded.length, ReaderTtsLexicalTokens.scan(folded)).text
    }

    private fun normalizeForSpeech(
        text: String,
        start: Int,
        end: Int,
        tokens: List<ReaderTtsLexicalTokens.Token>,
    ): NormalizedSpeech {
        val spoken = StringBuilder()
        val sourceRanges = mutableListOf<ReaderTtsCharacterRange>()
        var cursor = start
        var tokenIndex = 0
        var pendingSpaceStart = -1
        while (cursor < end) {
            val next = cursor + Character.charCount(text.codePointAt(cursor))
            while (tokenIndex < tokens.size && tokens[tokenIndex].end <= cursor) tokenIndex++
            val token = tokens.getOrNull(tokenIndex)
            val meaningfulSymbol = token != null && cursor >= token.start && token.kind != ReaderTtsLexicalTokens.Kind.WORD
            if (text[cursor].isWhitespace() || isNoisySymbol(text[cursor]) && !meaningfulSymbol) {
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
        val hints = tokens.filter { ReaderTtsLexicalTokens.needsContext(it, start, end) }.flatMap { token ->
            ReaderTtsLexicalTokens.readings(text, token, start, end).mapNotNull { reading ->
                val first = sourceRanges.indexOfFirst { it.startCharOffset >= reading.start && it.endCharOffset <= reading.end }
                val last = sourceRanges.indexOfLast { it.startCharOffset >= reading.start && it.endCharOffset <= reading.end }
                if (first < 0 || last < first) null else ReaderTtsNumberReadingHint(
                    first, last + 1, reading.kind, reading.suffix,
                )
            }
        }
        return NormalizedSpeech(spoken.toString(), sourceRanges.toList(), hints)
    }

    private fun isNoisySymbol(ch: Char): Boolean {
        return ch == '-' ||
            ch == '·' ||
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
        val numberReadingHints: List<ReaderTtsNumberReadingHint>,
    )
}
