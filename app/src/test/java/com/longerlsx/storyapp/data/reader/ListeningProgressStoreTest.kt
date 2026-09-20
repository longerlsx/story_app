package com.longerlsx.storyapp.data.reader

import com.longerlsx.storyapp.core.model.ListeningProgress
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.data.book.FileAnchorStore
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningProgressStoreTest {
    @Test
    fun twoBooksAndLatestCheckpointSurviveRecreationWithoutChangingReadingProgress() = runTest {
        val root = Files.createTempDirectory("story-listening-restore").toFile()
        try {
            FileAnchorStore(root).saveProgressAndLastOpened(
                ReadingProgress("reading", ReadingAnchor(3, 125), ReadingMode.PAGE, 100L),
            )
            val readingBytes = root.resolve("reader-anchors.properties").readBytes()
            val first = ListeningProgress("book.=:\\\n一", "书名\t第一卷\n续", 4, 213, "章名\n下篇", 200L)
            val second = ListeningProgress("book", "另一本", 9, 42, "尾声", 300L, completed = true)
            val store = ListeningProgressStore(root)
            store.save(first)
            store.save(second)
            val latestFirst = first.copy(charOffset = 345, updatedAt = 400L)
            store.save(latestFirst)

            val restored = ListeningProgressStore(root)
            assertEquals(latestFirst, restored.load(first.bookId))
            assertEquals(second, restored.load("book"))
            assertEquals(latestFirst, restored.loadLast())
            assertNull(restored.load("missing"))
            assertArrayEquals(readingBytes, root.resolve("reader-anchors.properties").readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidPositionDoesNotChangeExistingCheckpointOrLastBook() = runTest {
        val root = Files.createTempDirectory("story-listening-invalid").toFile()
        try {
            val store = ListeningProgressStore(root)
            val valid = ListeningProgress("a", "甲", 2, 90, "第二章", 10L)
            store.save(valid)
            val before = root.resolve("listening-progress.properties").readBytes()

            for (invalid in listOf(
                valid.copy(bookId = "b", chapterIndex = -1),
                valid.copy(bookId = "b", charOffset = -1),
                valid.copy(updatedAt = -1L),
            )) {
                assertTrue(runCatching { store.save(invalid) }.exceptionOrNull() is IllegalArgumentException)
                assertArrayEquals(before, root.resolve("listening-progress.properties").readBytes())
            }
            assertEquals(valid, ListeningProgressStore(root).loadLast())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptBookIsIsolatedAndReadsNeverRepairTheFile() = runTest {
        val root = Files.createTempDirectory("story-listening-corrupt-book").toFile()
        try {
            // URL-safe Base64 keys: YQ = a, Yg = b. This is a hand-written disk fixture.
            val file = root.resolve("listening-progress.properties")
            file.writeText(
                """
                book.YQ.bookTitle=Alpha
                book.YQ.chapterIndex=3
                book.YQ.charOffset=71
                book.YQ.chapterTitle=Three
                book.YQ.updatedAt=90
                book.YQ.completed=false
                book.Yg.bookTitle=Broken
                book.Yg.chapterIndex=2
                book.Yg.charOffset=invalid
                book.Yg.chapterTitle=Two
                book.Yg.updatedAt=91
                book.Yg.completed=true
                lastBookId=b
                """.trimIndent(),
            )
            val before = file.readBytes()
            val store = ListeningProgressStore(root)

            assertEquals(ListeningProgress("a", "Alpha", 3, 71, "Three", 90L), store.load("a"))
            assertNull(store.load("b"))
            assertNull(store.loadLast())
            assertArrayEquals(before, file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun brokenPropertiesReturnNoCheckpointAndCannotBeSilentlyOverwritten() = runTest {
        val root = Files.createTempDirectory("story-listening-corrupt-file").toFile()
        try {
            val file = root.resolve("listening-progress.properties")
            file.writeText("lastBookId=\\uZZZZ")
            val before = file.readBytes()
            val store = ListeningProgressStore(root)

            assertNull(store.load("a"))
            assertNull(store.loadLast())
            assertTrue(runCatching {
                store.save(ListeningProgress("a", "Alpha", 0, 0, "Start", 1L))
            }.isFailure)
            assertArrayEquals(before, file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentSavesFromOneStoreKeepEveryBookAndAConsistentLastCheckpoint() = runTest {
        val root = Files.createTempDirectory("story-listening-concurrent").toFile()
        try {
            val store = ListeningProgressStore(root)
            val start = CompletableDeferred<Unit>()
            val checkpoints = (0 until 8).map {
                ListeningProgress("book-$it", "Book $it", it, it * 10, "Chapter $it", it.toLong())
            }
            val writes = checkpoints.map { progress ->
                async(Dispatchers.Default) {
                    start.await()
                    store.save(progress)
                }
            }
            start.complete(Unit)
            writes.awaitAll()

            val restored = ListeningProgressStore(root)
            for (checkpoint in checkpoints) assertEquals(checkpoint, restored.load(checkpoint.bookId))
            assertTrue(restored.loadLast() in checkpoints)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun filesystemFailureIsReportedToTheCaller() = runTest {
        val root = Files.createTempDirectory("story-listening-write-failure").toFile()
        try {
            // An occupied destination must not look like a successful save.
            root.resolve("listening-progress.properties").mkdir()
            assertTrue(runCatching {
                ListeningProgressStore(root).save(ListeningProgress("a", "Alpha", 0, 0, "Start", 1L))
            }.exceptionOrNull() is IOException)
        } finally {
            root.deleteRecursively()
        }
    }
}
