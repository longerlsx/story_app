package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScrollFeedAnchorMapperTest {

    @Test
    fun mapsVisibleItemOffsetToChapterLocalCharOffset() {
        val charOffset = ReaderScrollFeedAnchorMapper.toCharOffset(
            contentLength = 1000,
            itemOffsetPx = -120,
            itemHeightPx = 400,
        )

        assertEquals(300, charOffset)
    }

    @Test
    fun restoresApproximateItemOffsetFromSavedCharOffset() {
        val itemOffset = ReaderScrollFeedAnchorMapper.toScrollOffsetPx(
            contentLength = 1000,
            charOffset = 250,
            itemHeightPx = 400,
        )

        assertEquals(100, itemOffset)
    }
}
