package com.longerlsx.storyapp.feature.reader

data class ReaderPageTurnDecision(
    val action: ReaderPageTurnAction,
    val boundaryTransition: ReaderBoundaryPageTransition? = null,
)

object ReaderPageTurnDecisionResolver {
    fun resolve(
        direction: ReaderPageTurnDirection,
        settledPage: Int,
        currentPages: List<ReaderPageSlice>,
        previousPages: List<ReaderPageSlice>,
        nextPages: List<ReaderPageSlice>,
        contentLoaded: Boolean,
        restoredPosition: Boolean,
        isScrollInProgress: Boolean,
        hasActiveBoundaryTransition: Boolean,
        previousChapterIndex: Int?,
        nextChapterIndex: Int?,
    ): ReaderPageTurnDecision {
        val action = ReaderPageBoundaryResolver.resolvePageTurn(
            direction = direction,
            settledPage = settledPage,
            pageCount = currentPages.size,
            contentLoaded = contentLoaded,
            restoredPosition = restoredPosition,
            isScrollInProgress = isScrollInProgress,
            hasActiveBoundaryTransition = hasActiveBoundaryTransition,
            previousChapterIndex = previousChapterIndex,
            nextChapterIndex = nextChapterIndex,
        )
        val transition = when (action) {
            is ReaderPageTurnAction.Chapter -> ReaderPageBoundaryTransitionResolver.resolve(
                direction = direction,
                settledPage = settledPage,
                currentPages = currentPages,
                previousPages = previousPages,
                nextPages = nextPages,
                targetChapterIndex = action.chapterIndex,
            )

            ReaderPageTurnAction.Ignore,
            is ReaderPageTurnAction.Page,
            -> null
        }
        return ReaderPageTurnDecision(
            action = action,
            boundaryTransition = transition,
        )
    }
}
