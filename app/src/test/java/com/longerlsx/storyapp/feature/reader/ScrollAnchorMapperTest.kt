package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollAnchorMapperTest {

    @Test
    fun mapsScrollPositionToApproximateCharOffset() {
        val charOffset = ScrollAnchorMapper.toCharOffset(
            contentLength = 1_000,
            scrollValue = 250,
            maxScrollValue = 500,
        )

        assertEquals(500, charOffset)
    }

    @Test
    fun mapsCharOffsetBackToScrollPosition() {
        val scrollValue = ScrollAnchorMapper.toScrollValue(
            contentLength = 1_000,
            charOffset = 500,
            maxScrollValue = 500,
        )

        assertEquals(250, scrollValue)
    }

    @Test
    fun clampsWhenContentFitsWithoutScroll() {
        assertEquals(
            0,
            ScrollAnchorMapper.toCharOffset(
                contentLength = 800,
                scrollValue = 0,
                maxScrollValue = 0,
            ),
        )
        assertEquals(
            0,
            ScrollAnchorMapper.toScrollValue(
                contentLength = 800,
                charOffset = 300,
                maxScrollValue = 0,
            ),
        )
    }
}
