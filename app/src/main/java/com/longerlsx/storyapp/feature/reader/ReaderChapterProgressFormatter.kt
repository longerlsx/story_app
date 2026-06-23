package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.data.book.ChapterDetectionRules
import com.longerlsx.storyapp.data.book.ChapterLine
import com.longerlsx.storyapp.data.book.ChapterTitleKind

object ReaderChapterProgressFormatter {
    private const val UnknownProgress = "--/--"

    fun format(
        chapters: List<Chapter>,
        selectedChapterPosition: Int,
    ): String {
        val selectedChapter = chapters.getOrNull(selectedChapterPosition) ?: return UnknownProgress
        if (!hasSyntheticLeadingPreface(chapters)) {
            return "${selectedChapterPosition + 1}/${chapters.size}章"
        }
        if (selectedChapterPosition == 0) {
            return selectedChapter.title.ifBlank { "前言" }
        }

        val displayChapterPositions = chapters
            .mapIndexedNotNull { index, chapter ->
                index.takeIf { isDisplayChapterTitle(chapter.title) }
            }
        val displayPosition = displayChapterPositions.indexOf(selectedChapterPosition)
        return if (displayPosition >= 0) {
            "${displayPosition + 1}/${displayChapterPositions.size}章"
        } else {
            selectedChapter.title.ifBlank { "${selectedChapterPosition + 1}/${chapters.size}章" }
        }
    }

    private fun hasSyntheticLeadingPreface(chapters: List<Chapter>): Boolean {
        return chapters.firstOrNull()?.title == "前言" &&
            chapters.drop(1).any { isDisplayChapterTitle(it.title) }
    }

    private fun isDisplayChapterTitle(title: String): Boolean {
        val chapterLine = ChapterLine.fromContent(title).singleOrNull() ?: return false
        return ChapterDetectionRules.matchAll(chapterLine).any { candidate ->
            candidate.kind == ChapterTitleKind.CHAPTER ||
                candidate.kind == ChapterTitleKind.SPECIAL ||
                candidate.kind == ChapterTitleKind.NUMERIC
        }
    }
}
