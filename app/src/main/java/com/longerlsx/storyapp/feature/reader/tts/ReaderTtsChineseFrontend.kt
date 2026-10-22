package com.longerlsx.storyapp.feature.reader.tts

/** Speech-only preparation; persisted positions always refer to the original segment. */
internal class ReaderTtsChineseFrontend(private val supportedCodePoints: Set<Int>) {
    fun prepare(segment: ReaderTtsSegment): String {
        val normalized = ReaderTtsChineseNumberNormalizer.normalize(segment)
        val output = StringBuilder()
        var previousWasLetter = false
        normalized.codePoints().forEach { original ->
            // CharacterLexicon lowercases dictionary keys while leaving input unchanged.
            val codePoint = if (original in 'A'.code..'Z'.code) original + 32 else original
            val letter = codePoint in 'a'.code..'z'.code
            when {
                Character.isWhitespace(codePoint) || codePoint in SILENT_DELIMITERS -> output.append(' ')
                else -> {
                    check(codePoint in supportedCodePoints) {
                        "离线声音暂不支持“${String(Character.toChars(codePoint))}”（U+${codePoint.toString(16).uppercase()}），本段未播放，请重试或换一处开始"
                    }
                    // sherpa merges adjacent ASCII letters into one word. Separate them
                    // so each explicit letter-name lexicon entry is used, never skipped.
                    if (letter && previousWasLetter) output.append(' ')
                    output.appendCodePoint(codePoint)
                }
            }
            previousWasLetter = letter
        }
        val prepared = output.toString().replace(WHITESPACE, " ").replace(BEFORE_PUNCTUATION, "$1").trim()
        check(prepared.isNotEmpty()) { "这段文字没有可朗读的内容，请换一处开始" }
        return prepared
    }

    companion object {
        // Only presentation delimiters are silent; unknown readable text must fail closed.
        private val SILENT_DELIMITERS = "\"'“”‘’「」『』（）()【】[]《》〈〉{}｛｝".codePoints().toArray().toSet()
        private val WHITESPACE = Regex("\\s+")
        private val BEFORE_PUNCTUATION = Regex(" +([。.？！?!…、，,：:；;])")
    }
}
