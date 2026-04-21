package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReadingProgress

object OpeningChapterSelector {
    fun select(
        chapters: List<Chapter>,
        progress: ReadingProgress?,
    ): Int {
        if (chapters.isEmpty()) {
            return 0
        }

        val savedChapterIndex = progress?.anchor?.chapterIndex
        if (savedChapterIndex != null && chapters.any { it.chapterIndex == savedChapterIndex }) {
            return savedChapterIndex
        }

        return chapters
            .firstOrNull { it.title != "前言" }
            ?.chapterIndex
            ?: chapters.first().chapterIndex
    }
}
