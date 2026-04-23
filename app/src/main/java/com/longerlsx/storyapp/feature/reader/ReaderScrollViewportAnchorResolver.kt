package com.longerlsx.storyapp.feature.reader

object ReaderScrollViewportAnchorResolver {
    fun resolveReadableViewportTopPx(
        scrollViewportTopInsetPx: Int,
        chromeBottomPx: Int?,
        extraSpacingPx: Int,
    ): Int {
        val obscuredTopPx = ((chromeBottomPx ?: scrollViewportTopInsetPx) - scrollViewportTopInsetPx)
            .coerceAtLeast(0)
        return obscuredTopPx + extraSpacingPx.coerceAtLeast(0)
    }
}
