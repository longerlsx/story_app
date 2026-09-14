package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ImportResult(
    val book: Book,
    val chapters: List<Chapter>,
    val duplicate: Boolean,
)

class ImportCoordinator(
    private val repository: BookRepository,
    private val storage: ImportedBookStorage,
    private val textContentLoader: TextContentLoader,
) {
    private val importMutex = Mutex()

    suspend fun importTxt(
        fileName: String,
        bytes: ByteArray,
        sourceType: ImportSourceType,
        importedAt: Long = System.currentTimeMillis(),
    ): ImportResult = withContext(Dispatchers.IO) {
        importMutex.withLock {
            val fileHash = FileHashCalculator.sha256(bytes)
            val existing = repository.findBookByHash(fileHash)
            if (existing != null) {
                val existingChapters = repository.getChapters(existing.id)
                return@withLock ImportResult(
                    book = existing,
                    chapters = existingChapters,
                    duplicate = true,
                )
            }

            val bookId = fileHash.take(16)
            val pending = storage.prepareImport(bookId, bytes)
            var published = false
            try {
                val loadedText = textContentLoader.loadNormalizedText(pending.sourceFile)
                val metadata = MetadataExtractor.extract(
                    fileName = fileName,
                    previewText = loadedText.normalizedText.take(4_000),
                )
                val parsedChapters = ChapterParser.parseNormalized(loadedText.normalizedText)
                require(parsedChapters.any { it.content.isNotBlank() }) { "文件中没有可阅读的正文。" }
                val chapters = parsedChapters.mapIndexed { index, parsed ->
                    Chapter(
                        bookId = bookId,
                        chapterIndex = index,
                        title = parsed.title,
                        startOffset = parsed.startOffset,
                        endOffset = parsed.endOffset,
                        wordCount = parsed.content.filterNot(Char::isWhitespace).length,
                    )
                }
                val book = Book(
                    id = bookId,
                    title = metadata.title,
                    author = metadata.author,
                    importSourceType = sourceType,
                    importFileName = fileName,
                    storedPath = pending.finalSourceFile.absolutePath,
                    charset = loadedText.charsetName,
                    fileHash = fileHash,
                    wordCount = loadedText.normalizedText.filterNot(Char::isWhitespace).length,
                    chapterCount = chapters.size,
                    importedAt = importedAt,
                    lastReadAt = importedAt,
                )
                coroutineContext.ensureActive()
                withContext(NonCancellable) {
                    storage.commitImport(pending, book, chapters)
                    repository.saveImportedBook(
                        book = book,
                        chapters = chapters,
                        chapterContents = parsedChapters.mapIndexed { index, parsed -> index to parsed.content }.toMap(),
                    )
                    published = true
                }

                ImportResult(
                    book = book,
                    chapters = chapters,
                    duplicate = false,
                )
            } finally {
                if (!published) storage.discardImport(pending)
            }
        }
    }
}
