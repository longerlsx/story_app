package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReadingProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryBookRepository(
    private val anchorStore: AnchorStore? = null,
) : BookRepository {
    private val mutex = Mutex()
    private val books = linkedMapOf<String, Book>()
    private val chapters = linkedMapOf<String, List<Chapter>>()
    private val chapterContents = linkedMapOf<String, Map<Int, String>>()
    private val progressByBook = linkedMapOf<String, ReadingProgress>()
    private val bookshelf = MutableStateFlow(emptyList<Book>())

    override fun observeBookshelf(): Flow<List<Book>> = bookshelf.asStateFlow()

    override suspend fun saveImportedBook(
        book: Book,
        chapters: List<Chapter>,
        chapterContents: Map<Int, String>,
    ) {
        mutex.withLock {
            books[book.id] = book
            this.chapters[book.id] = chapters.sortedBy(Chapter::chapterIndex)
            this.chapterContents[book.id] = chapterContents
            publishBookshelf()
        }
    }

    override suspend fun getBook(bookId: String): Book? = mutex.withLock {
        books[bookId]
    }

    override suspend fun findBookByHash(fileHash: String): Book? = mutex.withLock {
        books.values.firstOrNull { it.fileHash == fileHash }
    }

    override suspend fun getChapters(bookId: String): List<Chapter> = mutex.withLock {
        chapters[bookId].orEmpty()
    }

    override suspend fun getChapterText(bookId: String, chapterIndex: Int): String? = mutex.withLock {
        chapterContents[bookId]?.get(chapterIndex)
    }

    override suspend fun updateLastRead(bookId: String, lastReadAt: Long) {
        mutex.withLock {
            val book = books[bookId] ?: return
            books[bookId] = book.copy(lastReadAt = lastReadAt)
            publishBookshelf()
        }
    }

    override suspend fun saveReadingProgress(progress: ReadingProgress) {
        mutex.withLock {
            val current = progressByBook[progress.bookId]
            progressByBook[progress.bookId] = if (current == null || progress.updatedAt >= current.updatedAt) {
                progress
            } else {
                current
            }
            val book = books[progress.bookId]
            if (book != null && progress.updatedAt >= book.lastReadAt) {
                books[progress.bookId] = book.copy(lastReadAt = progress.updatedAt)
                publishBookshelf()
            }
        }
        anchorStore?.save(progress)
        anchorStore?.setLastOpenedBookId(progress.bookId)
    }

    override suspend fun getReadingProgress(bookId: String): ReadingProgress? = mutex.withLock {
        progressByBook[bookId] ?: anchorStore?.load(bookId)?.also { restored ->
            progressByBook[bookId] = restored
        }
    }

    suspend fun hydrateFromStorage(
        snapshots: List<StoredBookSnapshot>,
        textContentLoader: TextContentLoader,
    ) {
        snapshots.forEach { snapshot ->
            val loadedText = textContentLoader.loadNormalizedText(snapshot.sourceFile)
            val contents = snapshot.chapters.associate { chapter ->
                chapter.chapterIndex to loadedText.normalizedText
                    .substring(chapter.startOffset.coerceAtLeast(0), chapter.endOffset.coerceAtMost(loadedText.normalizedText.length))
                    .trim()
            }
            saveImportedBook(
                book = snapshot.book,
                chapters = snapshot.chapters,
                chapterContents = contents,
            )
        }
    }

    suspend fun hydrateProgress(progressItems: List<ReadingProgress>) {
        progressItems.forEach { progress ->
            saveReadingProgress(progress)
        }
    }

    private fun publishBookshelf() {
        bookshelf.value = books.values
            .sortedWith(
                compareByDescending<Book> { it.lastReadAt }
                    .thenByDescending { it.importedAt },
            )
    }
}
