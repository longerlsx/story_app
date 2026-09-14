package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReadingProgress
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BookContentReadException(val bookId: String, message: String, cause: Throwable? = null) :
    Exception(message, cause)

class InMemoryBookRepository(
    private val anchorStore: AnchorStore? = null,
    private val readText: suspend (File, TextContentLoader) -> LoadedTextContent = { file, loader ->
        loader.loadNormalizedText(file)
    },
) : BookRepository {
    private val mutex = Mutex()
    private val progressWriteMutex = Mutex()
    private val books = linkedMapOf<String, Book>()
    private val chapters = linkedMapOf<String, List<Chapter>>()
    private val contentSources = mutableMapOf<String, ContentSource>()
    private val contentCache = LinkedHashMap<String, Map<Int, String>>(4, 0.75f, true)
    private val progressByBook = linkedMapOf<String, ReadingProgress>()
    private val bookshelf = MutableStateFlow(emptyList<Book>())

    private class ContentSource(val load: suspend () -> Map<Int, String>) {
        val mutex = Mutex()
    }

    override fun observeBookshelf(): Flow<List<Book>> = bookshelf.asStateFlow()

    override suspend fun saveImportedBook(book: Book, chapters: List<Chapter>, chapterContents: Map<Int, String>) {
        val sortedChapters = chapters.sortedBy(Chapter::chapterIndex)
        val file = File(book.storedPath)
        val source = if (withContext(Dispatchers.IO) { file.isFile }) {
            fileSource(book.id, sortedChapters, file, TextContentLoader())
        } else {
            // The repository also supports books supplied entirely in memory.
            ContentSource { chapterContents }
        }
        mutex.withLock {
            books[book.id] = book
            this.chapters[book.id] = sortedChapters
            contentSources[book.id] = source
            cacheContents(book.id, chapterContents)
            publishBookshelf()
        }
    }

    override suspend fun getBook(bookId: String): Book? = mutex.withLock { books[bookId] }

    override suspend fun findBookByHash(fileHash: String): Book? = mutex.withLock {
        books.values.firstOrNull { it.fileHash == fileHash }
    }

    override suspend fun getChapters(bookId: String): List<Chapter> = mutex.withLock { chapters[bookId].orEmpty() }

    override suspend fun getChapterText(bookId: String, chapterIndex: Int): String? {
        val source = mutex.withLock {
            if (chapters[bookId].orEmpty().none { it.chapterIndex == chapterIndex }) return null
            contentCache[bookId]?.let { return it[chapterIndex] }
            contentSources[bookId] ?: return null
        }
        // Readers and TTS requesting the same book share one load, without blocking other books.
        return source.mutex.withLock {
            mutex.withLock { contentCache[bookId]?.let { return it[chapterIndex] } }
            val contents = withContext(Dispatchers.IO) { source.load() }
            mutex.withLock {
                if (contentSources[bookId] === source) cacheContents(bookId, contents)
            }
            contents[chapterIndex]
        }
    }

    override suspend fun updateLastRead(bookId: String, lastReadAt: Long) {
        mutex.withLock {
            val book = books[bookId] ?: return
            books[bookId] = book.copy(lastReadAt = lastReadAt)
            publishBookshelf()
        }
    }

    override suspend fun saveReadingProgress(progress: ReadingProgress) {
        progressWriteMutex.withLock {
            val current = getReadingProgress(progress.bookId)
            if (current != null && progress.updatedAt < current.updatedAt) return
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) { anchorStore?.saveProgressAndLastOpened(progress) }
                mutex.withLock {
                    acceptProgress(progress)
                    publishBookshelf()
                }
            }
        }
    }

    override suspend fun getReadingProgress(bookId: String): ReadingProgress? {
        mutex.withLock { progressByBook[bookId]?.let { return it } }
        val restored = withContext(Dispatchers.IO) { anchorStore?.load(bookId) }
        return mutex.withLock {
            val current = progressByBook[bookId]
            if (restored != null && (current == null || restored.updatedAt > current.updatedAt)) {
                progressByBook[bookId] = restored
                restored
            } else current
        }
    }

    suspend fun hydrateFromStorage(snapshots: List<StoredBookSnapshot>, textContentLoader: TextContentLoader) {
        mutex.withLock {
            snapshots.forEach { snapshot ->
                val id = snapshot.book.id
                books[id] = snapshot.book
                chapters[id] = snapshot.chapters
                contentSources[id] = fileSource(id, snapshot.chapters, snapshot.sourceFile, textContentLoader)
                contentCache.remove(id)
            }
            publishBookshelf()
        }
    }

    suspend fun hydrateProgress(progressItems: List<ReadingProgress>) {
        progressWriteMutex.withLock {
            mutex.withLock {
                progressItems.forEach(::acceptProgress)
                publishBookshelf()
            }
        }
    }

    suspend fun clearForTests() {
        progressWriteMutex.withLock {
            mutex.withLock {
                books.clear()
                chapters.clear()
                contentSources.clear()
                contentCache.clear()
                progressByBook.clear()
                publishBookshelf()
            }
        }
    }

    private fun fileSource(bookId: String, chapters: List<Chapter>, file: File, loader: TextContentLoader): ContentSource =
        ContentSource {
            try {
                val text = readText(file, loader).normalizedText
                validateChapters(bookId, chapters, chapters.size)
                require(chapters.all { it.endOffset <= text.length }) { "书籍正文与章节目录不匹配" }
                chapters.associate { it.chapterIndex to text.substring(it.startOffset, it.endOffset) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw BookContentReadException(bookId, "书籍正文无法读取", failure)
            }
        }

    private fun cacheContents(bookId: String, contents: Map<Int, String>) {
        contentCache[bookId] = contents
        while (contentCache.size > 2) contentCache.remove(contentCache.keys.first())
    }

    private fun acceptProgress(progress: ReadingProgress) {
        val current = progressByBook[progress.bookId]
        if (current != null && progress.updatedAt < current.updatedAt) return
        progressByBook[progress.bookId] = progress
        books[progress.bookId]?.let { book ->
            if (progress.updatedAt >= book.lastReadAt) books[progress.bookId] = book.copy(lastReadAt = progress.updatedAt)
        }
    }

    private fun publishBookshelf() {
        bookshelf.value = books.values.sortedWith(
            compareByDescending<Book> { it.lastReadAt }.thenByDescending { it.importedAt },
        )
    }
}
