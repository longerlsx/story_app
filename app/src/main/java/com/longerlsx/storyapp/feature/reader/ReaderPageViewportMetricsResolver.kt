package com.longerlsx.storyapp.feature.reader

data class ReaderPageViewportMetrics(
    val topPaddingPx: Int,
    val bottomPaddingPx: Int,
    val availableHeightPx: Int,
)

object ReaderPageViewportMetricsResolver {
    fun resolve(
        containerHeightPx: Int,
        baseTopPaddingPx: Int,
        baseBottomPaddingPx: Int,
        systemTopInsetPx: Int,
        systemBottomInsetPx: Int,
    ): ReaderPageViewportMetrics {
        val topPaddingPx = baseTopPaddingPx
        val bottomPaddingPx = baseBottomPaddingPx + systemBottomInsetPx
        val availableHeightPx = (containerHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(0)
        return ReaderPageViewportMetrics(
            topPaddingPx = topPaddingPx,
            bottomPaddingPx = bottomPaddingPx,
            availableHeightPx = availableHeightPx,
        )
    }
}
