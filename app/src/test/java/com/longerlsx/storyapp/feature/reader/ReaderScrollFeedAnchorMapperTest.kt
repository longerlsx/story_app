package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScrollFeedAnchorMapperTest {

    @Test
    fun toCharOffset_usesBodyGeometryOnly() {
        assertEquals(
            0,
            ReaderScrollFeedAnchorMapper.toCharOffset(
                contentLength = 400,
                bodyOffsetPx = 24,
                bodyHeightPx = 160,
            ),
        )
    }

    @Test
    fun toScrollOffsetPx_mapsWithinBodyGeometryOnly() {
        assertEquals(
            80,
            ReaderScrollFeedAnchorMapper.toScrollOffsetPx(
                contentLength = 400,
                charOffset = 200,
                bodyHeightPx = 160,
            ),
        )
    }

    @Test
    fun toCharOffset_respectsReadableViewportTopInset() {
        assertEquals(
            53,
            ReaderScrollFeedAnchorMapper.toCharOffset(
                contentLength = 400,
                bodyOffsetPx = 28,
                bodyHeightPx = 180,
                viewportTopPx = 52,
            ),
        )
    }

    @Test
    fun toItemScrollOffsetPx_alignsTargetToReadableViewportTop() {
        assertEquals(
            124,
            ReaderScrollFeedAnchorMapper.toItemScrollOffsetPx(
                contentLength = 400,
                charOffset = 200,
                bodyOffsetWithinItemPx = 96,
                bodyHeightPx = 160,
                viewportTopPx = 52,
            ),
        )
    }
}
