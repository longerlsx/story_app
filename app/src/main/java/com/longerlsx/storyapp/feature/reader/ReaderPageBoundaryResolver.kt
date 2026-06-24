package com.longerlsx.storyapp.feature.reader

enum class ReaderPageTurnDirection {
    PREVIOUS,
    NEXT,
}

sealed interface ReaderPageTurnAction {
    data object Ignore : ReaderPageTurnAction

    data class Page(
        val pageIndex: Int,
    ) : ReaderPageTurnAction

    data class Chapter(
        val chapterIndex: Int,
        val restoreToLastPage: Boolean,
    ) : ReaderPageTurnAction
}

object ReaderPageBoundaryResolver {
    fun resolvePageTurn(
        direction: ReaderPageTurnDirection,
        settledPage: Int,
        pageCount: Int,
        contentLoaded: Boolean,
        restoredPosition: Boolean,
        isScrollInProgress: Boolean,
        hasActiveBoundaryTransition: Boolean = false,
        previousChapterIndex: Int?,
        nextChapterIndex: Int?,
    ): ReaderPageTurnAction {
        if (!contentLoaded || !restoredPosition || isScrollInProgress || hasActiveBoundaryTransition || pageCount <= 0) {
            return ReaderPageTurnAction.Ignore
        }

        val activePage = settledPage.coerceIn(0, pageCount - 1)
        return when (direction) {
            ReaderPageTurnDirection.PREVIOUS -> {
                if (activePage > 0) {
                    ReaderPageTurnAction.Page(activePage - 1)
                } else {
                    previousChapterIndex?.let {
                        ReaderPageTurnAction.Chapter(
                            chapterIndex = it,
                            restoreToLastPage = true,
                        )
                    } ?: ReaderPageTurnAction.Ignore
                }
            }

            ReaderPageTurnDirection.NEXT -> {
                if (activePage < pageCount - 1) {
                    ReaderPageTurnAction.Page(activePage + 1)
                } else {
                    nextChapterIndex?.let {
                        ReaderPageTurnAction.Chapter(
                            chapterIndex = it,
                            restoreToLastPage = false,
                        )
                    } ?: ReaderPageTurnAction.Ignore
                }
            }
        }
    }
}
