package com.longerlsx.storyapp.feature.reader

data class ReaderBoundaryPageTransition(
    val direction: ReaderPageTurnDirection,
    val sourcePage: ReaderPageSlice,
    val previewPage: ReaderPageSlice,
    val targetChapterIndex: Int,
)

object ReaderPageBoundaryTransitionResolver {
    fun resolve(
        direction: ReaderPageTurnDirection,
        settledPage: Int,
        currentPages: List<ReaderPageSlice>,
        previousPages: List<ReaderPageSlice>,
        nextPages: List<ReaderPageSlice>,
        targetChapterIndex: Int,
    ): ReaderBoundaryPageTransition? {
        if (currentPages.isEmpty()) {
            return null
        }

        val sourcePage = currentPages[settledPage.coerceIn(0, currentPages.lastIndex)]
        val previewPage = when (direction) {
            ReaderPageTurnDirection.PREVIOUS -> previousPages.lastOrNull()
            ReaderPageTurnDirection.NEXT -> nextPages.firstOrNull()
        } ?: return null

        return ReaderBoundaryPageTransition(
            direction = direction,
            sourcePage = sourcePage,
            previewPage = previewPage,
            targetChapterIndex = targetChapterIndex,
        )
    }
}
