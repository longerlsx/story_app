package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReadingMode

data class ReaderSettingsAnchor(
    val chapterIndex: Int,
    val charOffset: Int,
)

object ReaderSettingsAnchorResolver {
    fun resolve(
        readingMode: ReadingMode,
        selectedChapterIndex: Int,
        pendingRestoreCharOffset: Int,
        currentPageIndex: Int,
        currentPages: List<ReaderPageSlice>,
        visibleItems: List<ReaderVisibleChapterItem>,
        chapters: List<Chapter>,
        chapterTextLengthByIndex: Map<Int, Int>,
        viewportTopPx: Int,
    ): ReaderSettingsAnchor {
        return when (readingMode) {
            ReadingMode.PAGE -> ReaderSettingsAnchor(
                chapterIndex = selectedChapterIndex,
                charOffset = currentPages
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        ReaderPageAnchorMapper.anchorForPageIndex(
                            pages = it,
                            pageIndex = currentPageIndex,
                        )
                    }
                    ?: pendingRestoreCharOffset,
            )

            ReadingMode.SCROLL -> resolveScrollAnchor(
                selectedChapterIndex = selectedChapterIndex,
                pendingRestoreCharOffset = pendingRestoreCharOffset,
                visibleItems = visibleItems,
                chapters = chapters,
                chapterTextLengthByIndex = chapterTextLengthByIndex,
                viewportTopPx = viewportTopPx,
            )
        }
    }

    private fun resolveScrollAnchor(
        selectedChapterIndex: Int,
        pendingRestoreCharOffset: Int,
        visibleItems: List<ReaderVisibleChapterItem>,
        chapters: List<Chapter>,
        chapterTextLengthByIndex: Map<Int, Int>,
        viewportTopPx: Int,
    ): ReaderSettingsAnchor {
        val activeChapterIndex = ReaderActiveChapterResolver.resolve(
            visibleItems = visibleItems,
            chapters = chapters,
            viewportTopPx = viewportTopPx,
        ) ?: return ReaderSettingsAnchor(
            chapterIndex = selectedChapterIndex,
            charOffset = pendingRestoreCharOffset,
        )
        val activeItem = visibleItems.firstOrNull { it.chapterIndex == activeChapterIndex }
            ?: return ReaderSettingsAnchor(
                chapterIndex = selectedChapterIndex,
                charOffset = pendingRestoreCharOffset,
            )
        val contentLength = chapterTextLengthByIndex[activeChapterIndex]
            ?: return ReaderSettingsAnchor(
                chapterIndex = selectedChapterIndex,
                charOffset = pendingRestoreCharOffset,
            )

        return ReaderSettingsAnchor(
            chapterIndex = activeChapterIndex,
            charOffset = ReaderScrollFeedAnchorMapper.toCharOffset(
                contentLength = contentLength,
                bodyOffsetPx = activeItem.bodyOffsetPx,
                bodyHeightPx = activeItem.bodyHeightPx,
                viewportTopPx = viewportTopPx,
            ),
        )
    }
}
