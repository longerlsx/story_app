package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryBookRepositoryTest {

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun concurrentReadersShareBodyLoadWithoutBlockingBookshelfAccess() = runTest {
        val root = Files.createTempDirectory("story-app-shared-body-load").toFile()
        val release = CompletableDeferred<Unit>()
        try {
            val started = CompletableDeferred<Unit>()
            val reads = AtomicInteger()
            val source = root.resolve("book.txt").apply { writeText("并发正文") }
            val book = sampleBook("shared", "并发读取")
            val repository = InMemoryBookRepository(readText = { file, loader ->
                reads.incrementAndGet()
                started.complete(Unit)
                release.await()
                loader.loadNormalizedText(file)
            })
            repository.hydrateFromStorage(
                listOf(StoredBookSnapshot(book, listOf(sampleChapter(book.id).copy(endOffset = 4)), source)),
                TextContentLoader(),
            )
            assertEquals(0, reads.get())
            val first = async { repository.getChapterText(book.id, 0) }
            started.await()
            val second = async { repository.getChapterText(book.id, 0) }
            runCurrent()

            assertEquals(book, repository.getBook(book.id))
            assertEquals(listOf(book), repository.observeBookshelf().first())
            assertEquals(1, reads.get())
            release.complete(Unit)
            assertEquals("并发正文", first.await())
            assertEquals("并发正文", second.await())
            assertEquals(1, reads.get())
        } finally {
            release.complete(Unit)
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectedOlderProgressDoesNotOverwriteDiskOrLastOpenedBook() = runTest {
        val root = Files.createTempDirectory("story-app-stale-progress").toFile()
        try {
            val store = FileAnchorStore(root)
            val repository = InMemoryBookRepository(store)
            val latest = ReadingProgress("book-a", ReadingAnchor(2, 88), ReadingMode.PAGE, 300L)
            repository.saveReadingProgress(latest)
            repository.saveReadingProgress(ReadingProgress("book-b", ReadingAnchor(1, 44), ReadingMode.SCROLL, 400L))
            val before = root.resolve("reader-anchors.properties").readBytes()

            repository.saveReadingProgress(latest.copy(anchor = ReadingAnchor(0, 1), updatedAt = 100L))

            assertEquals(latest, repository.getReadingProgress("book-a"))
            assertEquals(latest, FileAnchorStore(root).load("book-a"))
            assertEquals("book-b", FileAnchorStore(root).getLastOpenedBookId())
            assertArrayEquals(before, root.resolve("reader-anchors.properties").readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun hydratesProgressWithoutAnyDiskWritesOrChangingLastOpened() = runTest {
        val root = Files.createTempDirectory("story-app-readonly-hydrate").toFile()
        try {
            val store = FileAnchorStore(root)
            val progress = ReadingProgress("book-a", ReadingAnchor(2, 88), ReadingMode.PAGE, 300L)
            store.save(progress)
            store.setLastOpenedBookId("book-b")
            val before = root.resolve("reader-anchors.properties").readBytes()
            val repository = InMemoryBookRepository(store)

            repository.hydrateProgress(store.loadAll())

            assertEquals(progress, repository.getReadingProgress("book-a"))
            assertEquals("book-b", FileAnchorStore(root).getLastOpenedBookId())
            assertArrayEquals(before, root.resolve("reader-anchors.properties").readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun defersBodyReadUntilRequestedAndEvictsLeastRecentlyUsedBook() = runTest {
        val root = Files.createTempDirectory("story-app-lazy-books").toFile()
        try {
            val snapshots = (1..3).map { number ->
                val id = "book-$number"
                val source = root.resolve("$id.txt").apply { writeText("旧正文$number") }
                StoredBookSnapshot(
                    sampleBook(id, id).copy(storedPath = source.path),
                    listOf(sampleChapter(id).copy(endOffset = 4, wordCount = 4)),
                    source,
                )
            }
            val repository = InMemoryBookRepository()
            repository.hydrateFromStorage(snapshots, TextContentLoader())
            snapshots.forEachIndexed { index, snapshot -> snapshot.sourceFile.writeText("新正文${index + 1}") }

            assertEquals("新正文1", repository.getChapterText("book-1", 0))
            assertEquals("新正文2", repository.getChapterText("book-2", 0))
            snapshots[0].sourceFile.writeText("重加载1")
            snapshots[1].sourceFile.writeText("重加载2")
            assertEquals("新正文1", repository.getChapterText("book-1", 0))
            assertEquals("新正文3", repository.getChapterText("book-3", 0))
            assertEquals("重加载2", repository.getChapterText("book-2", 0))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun truncatedBodyFailsForThatBookInsteadOfSilentlyClippingChapter() = runTest {
        val root = Files.createTempDirectory("story-app-truncated-body").toFile()
        try {
            val source = root.resolve("broken.txt").apply { writeText("短") }
            val repository = InMemoryBookRepository()
            repository.hydrateFromStorage(
                listOf(StoredBookSnapshot(sampleBook("broken", "损坏"), listOf(sampleChapter("broken")), source)),
                TextContentLoader(),
            )
            repository.saveImportedBook(sampleBook("healthy", "健康"), listOf(sampleChapter("healthy")), mapOf(0 to "正常正文"))

            assertTrue(runCatching { repository.getChapterText("broken", 0) }.isFailure)
            assertEquals("正常正文", repository.getChapterText("healthy", 0))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importedBooksAreReturnedInReverseLastReadOrder() = runTest {
        val repository = InMemoryBookRepository()
        val olderBook = sampleBook(
            id = "book-older",
            title = "旧书",
            lastReadAt = 100L,
        )
        val newerBook = sampleBook(
            id = "book-newer",
            title = "新书",
            lastReadAt = 300L,
        )

        repository.saveImportedBook(
            book = olderBook,
            chapters = listOf(sampleChapter(bookId = olderBook.id)),
            chapterContents = mapOf(0 to "旧书正文"),
        )
        repository.saveImportedBook(
            book = newerBook,
            chapters = listOf(sampleChapter(bookId = newerBook.id)),
            chapterContents = mapOf(0 to "新书正文"),
        )

        val bookshelf = repository.observeBookshelf().first()

        assertEquals(listOf("book-newer", "book-older"), bookshelf.map(Book::id))
    }

    @Test
    fun saveAndRestoreAnchorReturnsLatestOffset() = runTest {
        val repository = InMemoryBookRepository()
        val book = sampleBook(id = "book-anchor", title = "锚点书")

        repository.saveImportedBook(
            book = book,
            chapters = listOf(sampleChapter(bookId = book.id)),
            chapterContents = mapOf(0 to "正文"),
        )

        repository.saveReadingProgress(
            ReadingProgress(
                bookId = book.id,
                anchor = ReadingAnchor(chapterIndex = 0, charOffset = 18),
                readingMode = ReadingMode.SCROLL,
                updatedAt = 100L,
            ),
        )
        repository.saveReadingProgress(
            ReadingProgress(
                bookId = book.id,
                anchor = ReadingAnchor(chapterIndex = 0, charOffset = 144),
                readingMode = ReadingMode.SCROLL,
                updatedAt = 300L,
            ),
        )

        val restored = repository.getReadingProgress(book.id)

        assertNotNull(restored)
        assertEquals(ReadingAnchor(chapterIndex = 0, charOffset = 144), restored!!.anchor)
    }

    private fun sampleBook(
        id: String,
        title: String,
        lastReadAt: Long = 0L,
    ): Book = Book(
        id = id,
        title = title,
        author = "作者",
        importSourceType = ImportSourceType.LOCAL_FILE,
        importFileName = "$title.txt",
        storedPath = "/books/$id/original.txt",
        charset = "UTF-8",
        fileHash = "$id-hash",
        wordCount = 1024,
        chapterCount = 1,
        importedAt = 10L,
        lastReadAt = lastReadAt,
    )

    private fun sampleChapter(bookId: String): Chapter = Chapter(
        bookId = bookId,
        chapterIndex = 0,
        title = "第1章 开始",
        startOffset = 0,
        endOffset = 20,
        wordCount = 20,
    )
}
