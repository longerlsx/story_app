package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsStartRequestTest {

    @Test
    fun storesRequiredStartFields() {
        val request = ReaderTtsStartRequest(
            bookId = "book-42",
            bookTitle = "Book 42",
            chapterIndex = 3,
            charOffset = 128,
            chapterTitleOrSummary = "Chapter 3",
            activeStateLabel = "阅读中",
        )

        assertEquals("book-42", request.bookId)
        assertEquals("Book 42", request.bookTitle)
        assertEquals(3, request.chapterIndex)
        assertEquals(128, request.charOffset)
        assertEquals("Chapter 3", request.chapterTitleOrSummary)
        assertEquals("阅读中", request.activeStateLabel)
    }
}
