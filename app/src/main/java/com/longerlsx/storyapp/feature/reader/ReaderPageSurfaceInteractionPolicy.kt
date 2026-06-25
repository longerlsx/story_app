package com.longerlsx.storyapp.feature.reader

data class ReaderPageSurfaceInteractions(
    val enableTextTap: Boolean,
    val enableLongPressRestart: Boolean,
)

object ReaderPageSurfaceInteractionPolicy {
    fun resolve(
        pageIndex: Int,
        currentPage: Int,
        enableTapToDismissExpandedChrome: Boolean,
        enableTtsRestartGesture: Boolean,
    ): ReaderPageSurfaceInteractions {
        return ReaderPageSurfaceInteractions(
            enableTextTap = enableTapToDismissExpandedChrome,
            enableLongPressRestart = enableTtsRestartGesture && pageIndex == currentPage,
        )
    }
}
