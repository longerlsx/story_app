package com.longerlsx.storyapp.feature.reader

object ReaderPageFollowScrollResolver {
    fun resolveTargetPage(
        pages: List<ReaderPageSlice>,
        followTargetCharOffset: Int?,
        currentPage: Int,
        restoredPosition: Boolean,
    ): Int? {
        val targetCharOffset = followTargetCharOffset ?: return null
        if (!restoredPosition || pages.isEmpty()) {
            return null
        }

        val targetPage = ReaderPageAnchorMapper.pageIndexForCharOffset(
            pages = pages,
            charOffset = targetCharOffset,
        )
        return targetPage.takeIf { it != currentPage }
    }
}
