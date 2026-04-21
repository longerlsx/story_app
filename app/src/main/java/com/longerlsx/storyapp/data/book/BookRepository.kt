package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBookshelf(): Flow<List<Book>>

    suspend fun saveImportedBook(
        book: Book,
        chapters: List<Chapter>,
        chapterContents: Map<Int, String>,
    )

    suspend fun getBook(bookId: String): Book?

    suspend fun findBookByHash(fileHash: String): Book?

    suspend fun getChapters(bookId: String): List<Chapter>

    suspend fun getChapterText(bookId: String, chapterIndex: Int): String?

    suspend fun updateLastRead(bookId: String, lastReadAt: Long)

    suspend fun saveReadingProgress(progress: ReadingProgress)

    suspend fun getReadingProgress(bookId: String): ReadingProgress?
}
