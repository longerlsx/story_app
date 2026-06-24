package com.longerlsx.storyapp.feature.reader

object ReaderPageBoundaryTransitionClearancePolicy {
    fun shouldClear(
        transitionTargetChapterIndex: Int?,
        currentChapterIndex: Int,
        contentLoaded: Boolean,
        restoredPosition: Boolean,
    ): Boolean {
        return transitionTargetChapterIndex == currentChapterIndex &&
            contentLoaded &&
            restoredPosition
    }
}
