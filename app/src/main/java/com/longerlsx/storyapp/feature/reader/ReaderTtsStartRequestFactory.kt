package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.feature.reader.tts.ReaderTextStartLocation
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsStartRequest

object ReaderTtsStartRequestFactory {
    private const val DEFAULT_CHAPTER_SUMMARY = "正文"
    private const val ACTIVE_STATE_LABEL = "朗读中"

    fun create(
        book: Book,
        chapters: List<Chapter>,
        startLocation: ReaderTextStartLocation,
    ): ReaderTtsStartRequest {
        return ReaderTtsStartRequest(
            bookId = book.id,
            bookTitle = book.title,
            chapterIndex = startLocation.chapterIndex,
            charOffset = startLocation.charOffset,
            chapterTitleOrSummary = chapters
                .firstOrNull { it.chapterIndex == startLocation.chapterIndex }
                ?.title
                ?: DEFAULT_CHAPTER_SUMMARY,
            activeStateLabel = ACTIVE_STATE_LABEL,
        )
    }
}
