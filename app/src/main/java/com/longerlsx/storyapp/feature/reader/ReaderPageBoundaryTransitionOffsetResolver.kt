package com.longerlsx.storyapp.feature.reader

import kotlin.math.roundToInt

data class ReaderPageBoundaryTransitionOffsets(
    val currentPageOffsetPx: Int,
    val previewPageOffsetPx: Int,
)

object ReaderPageBoundaryTransitionOffsetResolver {
    fun resolve(
        direction: ReaderPageTurnDirection,
        containerWidthPx: Int,
        progress: Float,
    ): ReaderPageBoundaryTransitionOffsets {
        val widthPx = containerWidthPx.coerceAtLeast(0)
        val clampedProgress = progress.coerceIn(0f, 1f)

        val currentPageOffsetPx: Float
        val previewPageOffsetPx: Float
        when (direction) {
            ReaderPageTurnDirection.NEXT -> {
                currentPageOffsetPx = -widthPx * clampedProgress
                previewPageOffsetPx = widthPx * (1f - clampedProgress)
            }

            ReaderPageTurnDirection.PREVIOUS -> {
                currentPageOffsetPx = widthPx * clampedProgress
                previewPageOffsetPx = -widthPx * (1f - clampedProgress)
            }
        }

        return ReaderPageBoundaryTransitionOffsets(
            currentPageOffsetPx = currentPageOffsetPx.roundToInt(),
            previewPageOffsetPx = previewPageOffsetPx.roundToInt(),
        )
    }
}
