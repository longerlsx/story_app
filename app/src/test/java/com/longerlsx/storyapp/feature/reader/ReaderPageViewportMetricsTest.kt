package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageViewportMetricsTest {

    @Test
    fun addsSystemInsetsIntoPageViewportMetrics() {
        val metrics = ReaderPageViewportMetricsResolver.resolve(
            containerHeightPx = 2400,
            baseTopPaddingPx = 44,
            baseBottomPaddingPx = 56,
            systemTopInsetPx = 96,
            systemBottomInsetPx = 84,
        )

        assertEquals(44, metrics.topPaddingPx)
        assertEquals(140, metrics.bottomPaddingPx)
        assertEquals(2216, metrics.availableHeightPx)
    }

    @Test
    fun neverReturnsNegativeAvailableHeight() {
        val metrics = ReaderPageViewportMetricsResolver.resolve(
            containerHeightPx = 120,
            baseTopPaddingPx = 44,
            baseBottomPaddingPx = 56,
            systemTopInsetPx = 60,
            systemBottomInsetPx = 40,
        )

        assertEquals(0, metrics.availableHeightPx)
    }
}
