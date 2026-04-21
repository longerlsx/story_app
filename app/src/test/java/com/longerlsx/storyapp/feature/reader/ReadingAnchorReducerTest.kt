package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingAnchorReducerTest {

    @Test
    fun saveAndRestoreAnchorReturnsLatestOffset() {
        val initial = ReadingProgress(
            bookId = "book-1",
            anchor = ReadingAnchor(chapterIndex = 1, charOffset = 24),
            readingMode = ReadingMode.SCROLL,
            updatedAt = 100L,
        )

        val reduced = ReadingAnchorReducer.reduce(
            current = initial,
            nextAnchor = ReadingAnchor(chapterIndex = 1, charOffset = 108),
            nextMode = ReadingMode.PAGE,
            updatedAt = 200L,
        )

        assertEquals(ReadingAnchor(chapterIndex = 1, charOffset = 108), reduced.anchor)
        assertEquals(ReadingMode.PAGE, reduced.readingMode)
        assertEquals(200L, reduced.updatedAt)
    }

    @Test
    fun latestAnchorWinsAfterMultipleUpdates() {
        val initial = ReadingProgress(
            bookId = "book-1",
            anchor = ReadingAnchor(chapterIndex = 0, charOffset = 10),
            readingMode = ReadingMode.SCROLL,
            updatedAt = 100L,
        )
        val newer = ReadingAnchorReducer.reduce(
            current = initial,
            nextAnchor = ReadingAnchor(chapterIndex = 0, charOffset = 120),
            nextMode = ReadingMode.SCROLL,
            updatedAt = 300L,
        )
        val stale = ReadingAnchorReducer.reduce(
            current = newer,
            nextAnchor = ReadingAnchor(chapterIndex = 0, charOffset = 30),
            nextMode = ReadingMode.PAGE,
            updatedAt = 200L,
        )

        assertEquals(ReadingAnchor(chapterIndex = 0, charOffset = 120), stale.anchor)
        assertEquals(ReadingMode.SCROLL, stale.readingMode)
        assertEquals(300L, stale.updatedAt)
    }
}
