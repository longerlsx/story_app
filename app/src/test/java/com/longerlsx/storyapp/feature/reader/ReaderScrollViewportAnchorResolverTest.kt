package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScrollViewportAnchorResolverTest {

    @Test
    fun resolvesReadableViewportTopFromChromeBottomInsideScrollViewport() {
        assertEquals(
            12,
            ReaderScrollViewportAnchorResolver.resolveReadableViewportTopPx(
                scrollViewportTopInsetPx = 52,
                chromeBottomPx = 56,
                extraSpacingPx = 8,
            ),
        )
    }

    @Test
    fun resolvesReadableViewportTopFromImmersiveHeaderBottomInsideScrollViewport() {
        assertEquals(
            8,
            ReaderScrollViewportAnchorResolver.resolveReadableViewportTopPx(
                scrollViewportTopInsetPx = 52,
                chromeBottomPx = 46,
                extraSpacingPx = 8,
            ),
        )
    }

    @Test
    fun fallsBackToExtraSpacingWhenChromeBottomHasNotBeenMeasured() {
        assertEquals(
            8,
            ReaderScrollViewportAnchorResolver.resolveReadableViewportTopPx(
                scrollViewportTopInsetPx = 52,
                chromeBottomPx = null,
                extraSpacingPx = 8,
            ),
        )
    }
}
