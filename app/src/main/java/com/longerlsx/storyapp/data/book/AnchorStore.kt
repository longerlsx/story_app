package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import java.io.File
import java.util.Properties

interface AnchorStore {
    fun save(progress: ReadingProgress)

    fun saveProgressAndLastOpened(progress: ReadingProgress) {
        save(progress)
        setLastOpenedBookId(progress.bookId)
    }

    fun load(bookId: String): ReadingProgress?

    fun loadAll(): List<ReadingProgress>

    fun setLastOpenedBookId(bookId: String)

    fun getLastOpenedBookId(): String?
}

class FileAnchorStore(
    private val rootDir: File,
) : AnchorStore {
    private val anchorFile = File(rootDir, "reader-anchors.properties")

    @Synchronized
    override fun save(progress: ReadingProgress) {
        val properties = loadProperties()
        properties.putProgress(progress)
        storeProperties(properties)
    }

    @Synchronized
    override fun saveProgressAndLastOpened(progress: ReadingProgress) {
        val properties = loadProperties()
        properties.putProgress(progress)
        properties.setProperty(KEY_LAST_OPENED_BOOK_ID, progress.bookId)
        storeProperties(properties)
    }

    private fun Properties.putProgress(progress: ReadingProgress) {
        val prefix = "progress.${progress.bookId}"
        setProperty("$prefix.chapterIndex", progress.anchor.chapterIndex.toString())
        setProperty("$prefix.charOffset", progress.anchor.charOffset.toString())
        setProperty("$prefix.readingMode", progress.readingMode.name)
        setProperty("$prefix.updatedAt", progress.updatedAt.toString())
    }

    @Synchronized
    override fun load(bookId: String): ReadingProgress? {
        val properties = loadProperties()
        return properties.readProgress(bookId)
    }

    @Synchronized
    override fun loadAll(): List<ReadingProgress> {
        val properties = loadProperties()
        return properties.stringPropertyNames()
            .mapNotNull { key ->
                if (!key.startsWith("progress.") || !key.endsWith(".chapterIndex")) {
                    null
                } else {
                    key.removePrefix("progress.").removeSuffix(".chapterIndex")
                }
            }
            .distinct()
            .mapNotNull { bookId ->
                properties.readProgress(bookId)
            }
    }

    @Synchronized
    override fun setLastOpenedBookId(bookId: String) {
        val properties = loadProperties()
        properties.setProperty(KEY_LAST_OPENED_BOOK_ID, bookId)
        storeProperties(properties)
    }

    @Synchronized
    override fun getLastOpenedBookId(): String? {
        return loadProperties().getProperty(KEY_LAST_OPENED_BOOK_ID)
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        if (anchorFile.exists()) {
            anchorFile.inputStream().use(properties::load)
        }
        return properties
    }

    private fun storeProperties(properties: Properties) {
        writeFileAtomically(anchorFile) { output ->
            properties.store(output, null)
        }
    }

    private fun Properties.readProgress(bookId: String): ReadingProgress? {
        val prefix = "progress.$bookId"
        val chapterIndex = getProperty("$prefix.chapterIndex")?.toIntOrNull() ?: return null
        val charOffset = getProperty("$prefix.charOffset")?.toIntOrNull() ?: return null
        val readingMode = ReadingMode.entries.firstOrNull { it.name == getProperty("$prefix.readingMode") }
            ?: return null
        val updatedAt = getProperty("$prefix.updatedAt")?.toLongOrNull() ?: return null
        if (chapterIndex < 0 || charOffset < 0 || updatedAt < 0) return null

        return ReadingProgress(
            bookId = bookId,
            anchor = ReadingAnchor(
                chapterIndex = chapterIndex,
                charOffset = charOffset,
            ),
            readingMode = readingMode,
            updatedAt = updatedAt,
        )
    }

    private companion object {
        const val KEY_LAST_OPENED_BOOK_ID = "lastOpenedBookId"
    }
}
