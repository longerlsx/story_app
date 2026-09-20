package com.longerlsx.storyapp.data.reader

import com.longerlsx.storyapp.core.model.ListeningProgress
import com.longerlsx.storyapp.data.book.writeFileAtomically
import java.io.File
import java.util.Base64
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ListeningProgressStore(filesDir: File) {
    private val progressFile = File(filesDir, "listening-progress.properties")
    private val mutex = Mutex()

    suspend fun save(progress: ListeningProgress) {
        require(progress.bookId.isNotBlank()) { "Listening progress requires a book ID" }
        require(progress.chapterIndex >= 0 && progress.charOffset >= 0 && progress.updatedAt >= 0) {
            "Listening progress cannot contain negative positions or timestamps"
        }
        mutex.withLock {
            withContext(Dispatchers.IO) {
                // A malformed existing file must not silently become an empty replacement.
                val properties = readProperties()
                val prefix = keyPrefix(progress.bookId)
                properties.setProperty("$prefix.bookTitle", progress.bookTitle)
                properties.setProperty("$prefix.chapterIndex", progress.chapterIndex.toString())
                properties.setProperty("$prefix.charOffset", progress.charOffset.toString())
                properties.setProperty("$prefix.chapterTitle", progress.chapterTitle)
                properties.setProperty("$prefix.updatedAt", progress.updatedAt.toString())
                properties.setProperty("$prefix.completed", progress.completed.toString())
                properties.setProperty(KEY_LAST_BOOK_ID, progress.bookId)
                writeFileAtomically(progressFile) { properties.store(it, null) }
            }
        }
    }

    suspend fun load(bookId: String): ListeningProgress? = mutex.withLock {
        withContext(Dispatchers.IO) {
            readPropertiesForRestore()?.readProgress(bookId)
        }
    }

    suspend fun loadLast(): ListeningProgress? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val properties = readPropertiesForRestore() ?: return@withContext null
            val bookId = properties.getProperty(KEY_LAST_BOOK_ID) ?: return@withContext null
            properties.readProgress(bookId)
        }
    }

    private fun readProperties(): Properties = Properties().apply {
        if (progressFile.exists()) progressFile.inputStream().use(::load)
    }

    // Invalid Properties syntax (for example a malformed Unicode escape) invalidates
    // this file as a whole. Restoration remains read-only; IO errors still reach callers.
    private fun readPropertiesForRestore(): Properties? = try {
        readProperties()
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun Properties.readProgress(bookId: String): ListeningProgress? {
        if (bookId.isBlank()) return null
        val prefix = keyPrefix(bookId)
        val bookTitle = getProperty("$prefix.bookTitle") ?: return null
        val chapterIndex = getProperty("$prefix.chapterIndex")?.toIntOrNull() ?: return null
        val charOffset = getProperty("$prefix.charOffset")?.toIntOrNull() ?: return null
        val chapterTitle = getProperty("$prefix.chapterTitle") ?: return null
        val updatedAt = getProperty("$prefix.updatedAt")?.toLongOrNull() ?: return null
        val completed = getProperty("$prefix.completed")?.toBooleanStrictOrNull() ?: return null
        if (chapterIndex < 0 || charOffset < 0 || updatedAt < 0) return null
        return ListeningProgress(bookId, bookTitle, chapterIndex, charOffset, chapterTitle, updatedAt, completed)
    }

    private fun keyPrefix(bookId: String): String =
        "book." + Base64.getUrlEncoder().withoutPadding().encodeToString(bookId.toByteArray(Charsets.UTF_8))

    private companion object {
        const val KEY_LAST_BOOK_ID = "lastBookId"
    }
}
