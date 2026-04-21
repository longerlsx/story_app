package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnchorStoreTest {

    @Test
    fun savesAndLoadsProgressAcrossStoreRecreation() {
        val tempDir = Files.createTempDirectory("story-app-anchor-store-test").toFile()
        try {
            val progress = ReadingProgress(
                bookId = "book-1",
                anchor = ReadingAnchor(chapterIndex = 2, charOffset = 88),
                readingMode = ReadingMode.PAGE,
                updatedAt = 1234L,
            )

            FileAnchorStore(tempDir).save(progress)
            val restored = FileAnchorStore(tempDir).load("book-1")

            assertEquals(progress, restored)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun storesAndReturnsLastOpenedBookId() {
        val tempDir = Files.createTempDirectory("story-app-anchor-last-book-test").toFile()
        try {
            val store = FileAnchorStore(tempDir)

            assertNull(store.getLastOpenedBookId())

            store.setLastOpenedBookId("book-2")

            assertEquals("book-2", FileAnchorStore(tempDir).getLastOpenedBookId())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
