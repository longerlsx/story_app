package com.longerlsx.storyapp.data.book

import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType

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
    suspend fun importTxt(
        fileName: String,
        bytes: ByteArray,
        sourceType: ImportSourceType,
        importedAt: Long = System.currentTimeMillis(),
    ): ImportResult {
        val fileHash = FileHashCalculator.sha256(bytes)
        val existing = repository.findBookByHash(fileHash)
        if (existing != null) {
            val existingChapters = repository.getChapters(existing.id)
            return ImportResult(
                book = existing,
                chapters = existingChapters,
                duplicate = true,
            )
        }

        val bookId = fileHash.take(16)
        val storedFile = storage.copyImportedFile(
            bookId = bookId,
            bytes = bytes,
        )
        val loadedText = textContentLoader.loadNormalizedText(storedFile)
        val metadata = MetadataExtractor.extract(
            fileName = fileName,
            previewText = loadedText.normalizedText.take(4_000),
        )
        val parsedChapters = ChapterParser.parse(loadedText.normalizedText)
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
            storedPath = storedFile.absolutePath,
            charset = loadedText.charsetName,
            fileHash = fileHash,
            wordCount = loadedText.normalizedText.filterNot(Char::isWhitespace).length,
            chapterCount = chapters.size,
            importedAt = importedAt,
            lastReadAt = importedAt,
        )
        storage.persistCatalog(
            book = book,
            chapters = chapters,
        )

        repository.saveImportedBook(
            book = book,
            chapters = chapters,
            chapterContents = parsedChapters.mapIndexed { index, parsed -> index to parsed.content }.toMap(),
        )

        return ImportResult(
            book = book,
            chapters = chapters,
            duplicate = false,
        )
    }
}
