package com.longerlsx.storyapp.data.book

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedBookStorageTest {

    @Test
    fun copiesImportedFileIntoBookPrivateDirectory() {
        val tempDir = Files.createTempDirectory("story-app-storage-test")
        try {
            val rootDir = tempDir.toFile()
            val storage = ImportedBookStorage(rootDir)

            val copied = storage.copyImportedFile(
                bookId = "book-1",
                bytes = "第一章 测试内容".encodeToByteArray(),
            )

            assertEquals(
                rootDir.resolve("books/book-1/original.txt").absolutePath,
                copied.absolutePath,
            )
            assertEquals("第一章 测试内容", copied.readText())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
