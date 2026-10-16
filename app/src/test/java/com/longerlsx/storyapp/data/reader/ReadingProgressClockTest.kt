package com.longerlsx.storyapp.data.reader

import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.data.book.FileAnchorStore
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProgressClockTest {
    @Test
    fun newCapturesRemainOrderedAcrossBackwardAndForwardClockChanges() {
        var now = 1_000L
        val clock = ReadingProgressClock { now }
        val first = clock.nextTimestamp()
        now = 900L
        val afterRollback = clock.nextTimestamp()
        val sameMillisecond = clock.nextTimestamp()
        now = 2_000L
        val afterForwardCorrection = clock.nextTimestamp()

        assertEquals(1_000L, first)
        assertTrue(afterRollback > first)
        assertTrue(sameMillisecond > afterRollback)
        assertEquals(2_000L, afterForwardCorrection)
    }

    @Test
    fun restoredFutureCheckpointAllowsNewPositionWithoutAcceptingLateOldWrite() = runTest {
        val root = Files.createTempDirectory("story-progress-clock").toFile()
        try {
            val old = ReadingProgress("book", ReadingAnchor(0, 0), ReadingMode.PAGE, 60_000L)
            FileAnchorStore(root).saveProgressAndLastOpened(old)
            val store = FileAnchorStore(root)
            val restored = store.loadAll()
            val repository = InMemoryBookRepository(store)
            repository.hydrateProgress(restored)
            val clock = ReadingProgressClock { 1_000L }
            restored.forEach { clock.observe(it.updatedAt) }
            val current = old.copy(anchor = ReadingAnchor(1, 12), updatedAt = clock.nextTimestamp())

            repository.saveReadingProgress(current)
            clock.observe(old.updatedAt)
            repository.saveReadingProgress(old)

            assertEquals(current, repository.getReadingProgress(old.bookId))
            assertEquals(current, FileAnchorStore(root).load(old.bookId))
            assertEquals("book", FileAnchorStore(root).getLastOpenedBookId())
            assertTrue("Observing an old request must not rewind capture order", clock.nextTimestamp() > current.updatedAt)
        } finally {
            root.deleteRecursively()
        }
    }
}
