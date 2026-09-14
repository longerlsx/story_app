package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

data class StoredBookSnapshot(
    val book: Book,
    val chapters: List<Chapter>,
    val sourceFile: File,
)

data class BookStorageIssue(val bookId: String, val message: String)

data class CatalogRestoreResult(
    val snapshots: List<StoredBookSnapshot>,
    val issues: List<BookStorageIssue>,
)

internal class PendingBookImport(val directory: File, val destination: File) {
    val sourceFile: File get() = File(directory, "original.txt")
    val finalSourceFile: File get() = File(destination, "original.txt")
    var committed = false
}

class ImportedBookStorage(private val rootDir: File) {
    fun copyImportedFile(bookId: String, bytes: ByteArray): File {
        val target = File(rootDir, "books/$bookId/$ORIGINAL_FILE_NAME")
        writeFileAtomically(target) { it.write(bytes) }
        return target
    }

    fun persistCatalog(book: Book, chapters: List<Chapter>) {
        persistCatalog(File(rootDir, "books/${book.id}"), book, chapters)
    }

    internal fun prepareImport(bookId: String, bytes: ByteArray): PendingBookImport {
        Files.createDirectories(rootDir.toPath())
        val pending = PendingBookImport(
            Files.createTempDirectory(rootDir.toPath(), ".import-").toFile(),
            File(rootDir, "books/$bookId"),
        )
        try {
            writeFileAtomically(pending.sourceFile) { it.write(bytes) }
            return pending
        } catch (failure: Exception) {
            pending.directory.deleteRecursively()
            throw failure
        }
    }

    internal fun commitImport(pending: PendingBookImport, book: Book, chapters: List<Chapter>) {
        check(!pending.destination.exists()) { "书籍目录已存在：${book.id}" }
        persistCatalog(pending.directory, book, chapters)
        Files.createDirectories(requireNotNull(pending.destination.parentFile).toPath())
        Files.move(pending.directory.toPath(), pending.destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        pending.committed = true
    }

    internal fun discardImport(pending: PendingBookImport) {
        pending.directory.deleteRecursively()
        if (pending.committed) pending.destination.deleteRecursively()
    }

    private fun persistCatalog(bookDir: File, book: Book, chapters: List<Chapter>) {
        val sortedChapters = chapters.sortedBy(Chapter::chapterIndex)
        validateChapters(book.id, sortedChapters, book.chapterCount)
        val catalog = buildString {
            sortedChapters.forEach { chapter ->
                append(chapter.chapterIndex)
                append('\t')
                append(chapter.title.replace('\n', ' ').replace('\r', ' '))
                append('\t')
                append(chapter.startOffset)
                append('\t')
                append(chapter.endOffset)
                append('\t')
                append(chapter.wordCount)
                append('\n')
            }
        }
        writeFileAtomically(File(bookDir, CHAPTERS_FILE_NAME)) { it.write(catalog.toByteArray(Charsets.UTF_8)) }
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
        // Metadata is the completion marker; never publish it before the catalog.
        writeFileAtomically(File(bookDir, METADATA_FILE_NAME)) { properties.store(it, null) }
    }

    fun restoreCatalog(): List<StoredBookSnapshot> = restoreCatalogWithIssues().snapshots

    fun restoreCatalogWithIssues(): CatalogRestoreResult {
        val snapshots = mutableListOf<StoredBookSnapshot>()
        val issues = mutableListOf<BookStorageIssue>()
        File(rootDir, "books").listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            try {
                snapshots += restoreBookSnapshot(directory)
            } catch (failure: Exception) {
                issues += BookStorageIssue(directory.name, failure.message ?: "书籍目录无法读取")
            }
        }
        return CatalogRestoreResult(snapshots, issues)
    }

    private fun restoreBookSnapshot(bookDir: File): StoredBookSnapshot {
        val metadataFile = File(bookDir, METADATA_FILE_NAME)
        val chapterFile = File(bookDir, CHAPTERS_FILE_NAME)
        val sourceFile = File(bookDir, ORIGINAL_FILE_NAME)
        require(metadataFile.isFile && chapterFile.isFile && sourceFile.isFile) { "书籍文件不完整" }
        val properties = Properties().apply { metadataFile.inputStream().use(::load) }
        fun required(key: String): String = requireNotNull(properties.getProperty(key)) { "缺少书籍字段：$key" }
        fun integer(key: String, default: Int): Int = properties.getProperty(key)?.toInt() ?: default
        fun timestamp(key: String): Long = properties.getProperty(key)?.toLong() ?: 0L
        val id = required("id")
        require(id == bookDir.name) { "书籍标识与目录不一致" }
        val chapters = chapterFile.readLines().filter(String::isNotBlank).map { line ->
            val parts = line.split('\t')
            require(parts.size >= 5) { "章节目录行不完整" }
            val tail = parts.size - 3
            Chapter(
                bookId = id,
                chapterIndex = parts.first().toInt(),
                title = parts.subList(1, tail).joinToString("\t"),
                startOffset = parts[tail].toInt(),
                endOffset = parts[tail + 1].toInt(),
                wordCount = parts[tail + 2].toInt(),
            )
        }
        val book = Book(
            id = id,
            title = required("title"),
            author = properties.getProperty("author").takeUnless { it.isNullOrBlank() },
            importSourceType = ImportSourceType.valueOf(required("importSourceType")),
            importFileName = required("importFileName"),
            storedPath = properties.getProperty("storedPath") ?: sourceFile.absolutePath,
            charset = properties.getProperty("charset") ?: "UTF-8",
            fileHash = required("fileHash"),
            wordCount = integer("wordCount", 0),
            chapterCount = integer("chapterCount", chapters.size),
            importedAt = timestamp("importedAt"),
            lastReadAt = timestamp("lastReadAt"),
        )
        require(book.wordCount >= 0 && book.importedAt >= 0 && book.lastReadAt >= 0) { "书籍元数据数值无效" }
        validateChapters(id, chapters, book.chapterCount)
        return StoredBookSnapshot(book, chapters, sourceFile)
    }

    private companion object {
        const val ORIGINAL_FILE_NAME = "original.txt"
        const val METADATA_FILE_NAME = "metadata.properties"
        const val CHAPTERS_FILE_NAME = "chapters.tsv"
    }
}

internal fun validateChapters(bookId: String, chapters: List<Chapter>, expectedCount: Int) {
    require(expectedCount == chapters.size) { "章节数量与元数据不一致" }
    var previousEnd = 0
    chapters.forEachIndexed { index, chapter ->
        require(chapter.bookId == bookId && chapter.chapterIndex == index) { "章节标识或顺序无效" }
        require(chapter.startOffset >= previousEnd && chapter.endOffset >= chapter.startOffset && chapter.wordCount >= 0) {
            "章节偏移或字数无效"
        }
        previousEnd = chapter.endOffset
    }
}
