package com.longerlsx.storyapp.feature.reader

data class ReaderBoundaryTarget(
    val chapterIndex: Int,
    val charOffset: Int,
)

object ReaderPageBoundaryResolver {
    fun previousChapterTarget(
        previousChapterIndex: Int,
        previousChapterPages: List<ReaderPageSlice>,
    ): ReaderBoundaryTarget {
        val lastPage = previousChapterPages.lastOrNull()
        return ReaderBoundaryTarget(
            chapterIndex = previousChapterIndex,
            charOffset = lastPage?.startCharOffset ?: 0,
        )
    }
}
