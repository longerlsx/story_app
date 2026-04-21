package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Chapter

object ReaderActiveChapterResolver {
    fun resolve(
        visibleItems: List<ReaderVisibleChapterItem>,
        chapters: List<Chapter>,
        viewportTopPx: Int,
    ): Int? {
        val primaryChapterIndex = ReaderScrollChapterResolver.resolveActiveChapter(
            visibleItems = visibleItems,
            viewportTopPx = viewportTopPx,
        ) ?: return null
        val chapterByIndex = chapters.associateBy { it.chapterIndex }
        if (chapterByIndex[primaryChapterIndex]?.title != "前言") {
            return primaryChapterIndex
        }

        return visibleItems
            .sortedBy { it.offsetPx }
            .firstOrNull { item ->
                chapterByIndex[item.chapterIndex]?.title != "前言"
            }
            ?.chapterIndex
            ?: primaryChapterIndex
    }
}
