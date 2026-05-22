package com.longerlsx.storyapp.data.book

object ChapterPatterns {
    fun isChapterTitle(line: String): Boolean {
        val chapterLine = ChapterLine.fromContent(line).singleOrNull() ?: return false
        return ChapterDetectionRules.matchAll(chapterLine).any { candidate ->
            candidate.kind == ChapterTitleKind.CHAPTER || candidate.kind == ChapterTitleKind.SPECIAL
        }
    }
}
