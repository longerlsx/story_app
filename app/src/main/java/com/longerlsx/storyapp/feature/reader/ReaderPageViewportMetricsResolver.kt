package com.longerlsx.storyapp.feature.reader

import kotlin.math.roundToInt

data class ReaderPageViewportMetrics(
    val topPaddingPx: Int,
    val bottomPaddingPx: Int,
    val availableHeightPx: Int,
)

object ReaderPageViewportMetricsResolver {
    fun availableWidthPx(containerWidthPx: Int, horizontalPaddingPx: Int): Int =
        (containerWidthPx - horizontalPaddingPx * 2).coerceAtLeast(0)

    // Column's spacedBy rounds each gap independently before placing its children.
    fun paragraphSpacingPx(spacingPx: Float): Float = spacingPx.roundToInt().toFloat()

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
