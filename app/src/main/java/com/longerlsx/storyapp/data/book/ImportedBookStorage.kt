package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import java.io.File
import java.util.Properties

data class StoredBookSnapshot(
    val book: Book,
    val chapters: List<Chapter>,
    val sourceFile: File,
)

class ImportedBookStorage(
    private val rootDir: File,
) {
    fun copyImportedFile(
        bookId: String,
        bytes: ByteArray,
    ): File {
        val bookDir = File(rootDir, "books/$bookId")
        if (!bookDir.exists()) {
            bookDir.mkdirs()
        }

        val targetFile = File(bookDir, "original.txt")
        targetFile.writeBytes(bytes)
        return targetFile
    }

    fun persistCatalog(
        book: Book,
        chapters: List<Chapter>,
    ) {
        val bookDir = File(rootDir, "books/${book.id}")
        if (!bookDir.exists()) {
            bookDir.mkdirs()
        }

        val metadataFile = File(bookDir, METADATA_FILE_NAME)
        val chapterFile = File(bookDir, CHAPTERS_FILE_NAME)
        val properties = Properties().apply {
            setProperty("id", book.id)
            setProperty("title", book.title)
            setProperty("author", book.author.orEmpty())
            setProperty("importSourceType", book.importSourceType.name)
            setProperty("importFileName", book.importFileName)
            setProperty("storedPath", book.storedPath)
            setProperty("charset", book.charset)
            setProperty("fileHash", book.fileHash)
            setProperty("wordCount", book.wordCount.toString())
            setProperty("chapterCount", book.chapterCount.toString())
            setProperty("importedAt", book.importedAt.toString())
            setProperty("lastReadAt", book.lastReadAt.toString())
        }
        metadataFile.outputStream().use { output ->
            properties.store(output, null)
        }
        chapterFile.writeText(
            buildString {
                chapters.sortedBy(Chapter::chapterIndex).forEach { chapter ->
                    append(chapter.chapterIndex)
                    append('\t')
                    append(chapter.title.replace('\n', ' '))
                    append('\t')
                    append(chapter.startOffset)
                    append('\t')
                    append(chapter.endOffset)
                    append('\t')
                    append(chapter.wordCount)
                    append('\n')
                }
            },
        )
    }

    fun restoreCatalog(): List<StoredBookSnapshot> {
        val booksRoot = File(rootDir, "books")
        if (!booksRoot.exists()) {
            return emptyList()
        }

        return booksRoot.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .mapNotNull(::restoreBookSnapshot)
    }

    private fun restoreBookSnapshot(bookDir: File): StoredBookSnapshot? {
        val metadataFile = File(bookDir, METADATA_FILE_NAME)
        val chapterFile = File(bookDir, CHAPTERS_FILE_NAME)
        val sourceFile = File(bookDir, ORIGINAL_FILE_NAME)
        if (!metadataFile.exists() || !chapterFile.exists() || !sourceFile.exists()) {
            return null
        }

        val properties = Properties().apply {
            metadataFile.inputStream().use(::load)
        }
        val id = properties.getProperty("id") ?: return null
        val chapters = chapterFile.readLines()
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 5) {
                    null
                } else {
                    Chapter(
                        bookId = id,
                        chapterIndex = parts[0].toInt(),
                        title = parts[1],
                        startOffset = parts[2].toInt(),
                        endOffset = parts[3].toInt(),
                        wordCount = parts[4].toInt(),
                    )
                }
            }

        val book = Book(
            id = id,
            title = properties.getProperty("title") ?: return null,
            author = properties.getProperty("author").takeUnless { it.isNullOrBlank() },
            importSourceType = properties.getProperty("importSourceType")
                ?.let(ImportSourceType::valueOf)
                ?: return null,
            importFileName = properties.getProperty("importFileName") ?: return null,
            storedPath = properties.getProperty("storedPath") ?: sourceFile.absolutePath,
            charset = properties.getProperty("charset") ?: "UTF-8",
            fileHash = properties.getProperty("fileHash") ?: return null,
            wordCount = properties.getProperty("wordCount")?.toIntOrNull() ?: 0,
            chapterCount = properties.getProperty("chapterCount")?.toIntOrNull() ?: chapters.size,
            importedAt = properties.getProperty("importedAt")?.toLongOrNull() ?: 0L,
            lastReadAt = properties.getProperty("lastReadAt")?.toLongOrNull() ?: 0L,
        )

        return StoredBookSnapshot(
            book = book,
            chapters = chapters,
            sourceFile = sourceFile,
        )
    }

    private companion object {
        const val ORIGINAL_FILE_NAME = "original.txt"
        const val METADATA_FILE_NAME = "metadata.properties"
        const val CHAPTERS_FILE_NAME = "chapters.tsv"
    }
}
