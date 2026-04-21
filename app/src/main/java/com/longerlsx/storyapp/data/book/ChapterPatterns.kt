package com.longerlsx.storyapp.data.book

object ChapterPatterns {
    val chapterTitleRegex = Regex(
        pattern = """^\s*(第[0-9一二三四五六七八九十百千零〇两]+章.*|序章|楔子|尾声)\s*$""",
    )

    fun isChapterTitle(line: String): Boolean {
        val trimmed = line.trim()
        if (!chapterTitleRegex.matches(trimmed)) {
            return false
        }

        if (trimmed.length > 40) {
            return false
        }

        return trimmed.lastOrNull() !in SENTENCE_ENDINGS
    }

    private val SENTENCE_ENDINGS = setOf(
        '。',
        '！',
        '？',
        '；',
        '，',
        ',',
        '.',
        '!',
        '?',
    )
}
