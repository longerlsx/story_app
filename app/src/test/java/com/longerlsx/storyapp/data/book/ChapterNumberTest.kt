package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterNumberTest {

    @Test
    fun parsesArabicAndPaddedArabicNumbers() {
        assertEquals(1, ChapterNumber.parse("1").value)
        assertEquals(1, ChapterNumber.parse("001").value)
    }

    @Test
    fun parsesCommonChineseNumbers() {
        assertEquals(1, ChapterNumber.parse("一").value)
        assertEquals(2, ChapterNumber.parse("二").value)
        assertEquals(10, ChapterNumber.parse("十").value)
        assertEquals(11, ChapterNumber.parse("十一").value)
        assertEquals(20, ChapterNumber.parse("二十").value)
        assertEquals(102, ChapterNumber.parse("一百零二").value)
        assertEquals(2, ChapterNumber.parse("两").value)
    }
}
