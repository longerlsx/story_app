package com.longerlsx.storyapp.feature.reader

object ReaderPageRestoreTargetResolver {
    fun resolve(
        pages: List<ReaderPageSlice>,
        restoreCharOffset: Int,
        restoreToLastPage: Boolean,
    ): Int {
        if (pages.isEmpty()) {
            return 0
        }
        return if (restoreToLastPage) {
            pages.lastIndex
        } else {
            ReaderPageAnchorMapper.pageIndexForCharOffset(
                pages = pages,
                charOffset = restoreCharOffset,
            )
        }
    }
}
