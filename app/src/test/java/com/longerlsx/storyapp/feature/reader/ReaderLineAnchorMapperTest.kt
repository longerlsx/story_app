package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLineAnchorMapperTest {
    private val lines = listOf(
        ReaderPageLine(0, 4, 0f, 20f),
        ReaderPageLine(6, 46, 40f, 60f),
        ReaderPageLine(46, 49, 60f, 80f),
    )

    @Test
    fun unevenParagraphLengthsUseTheActualVisibleLine() {
        assertEquals(6, ReaderLineAnchorMapper.charOffsetAtTop(lines, 45f))
        assertEquals(46, ReaderLineAnchorMapper.charOffsetAtTop(lines, 65f))
        assertEquals(6, ReaderLineAnchorMapper.charOffsetAtTop(lines, 30f))
    }

    @Test
    fun whitespaceAndChapterEndResolveToAnExistingTextLine() {
        assertEquals(40f, ReaderLineAnchorMapper.topForCharOffset(lines, 5), 0f)
        assertEquals(60f, ReaderLineAnchorMapper.topForCharOffset(lines, 49), 0f)
        assertEquals(40f, ReaderLineAnchorMapper.topForCharOffset(lines, 42), 0f)
    }
}
