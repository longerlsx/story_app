package com.longerlsx.storyapp.feature.reader

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderTestStateResetTest {

    @Test
    fun resetStoryAppStateClearsInMemoryBooks() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<StoryApplication>()
        val book = Book(
            id = "reset-state-book",
            title = "测试隔离书籍",
            author = "测试作者",
            importSourceType = ImportSourceType.EXTERNAL_INTENT,
            importFileName = "reset-state.txt",
            storedPath = "unused",
            charset = "UTF-8",
            fileHash = "reset-state-hash",
            wordCount = 4,
            chapterCount = 1,
            importedAt = 1L,
            lastReadAt = 1L,
        )
        application.bookRepository.saveImportedBook(
            book = book,
            chapters = listOf(
                Chapter(
                    bookId = book.id,
                    chapterIndex = 0,
                    title = "第1章 开始",
                    startOffset = 0,
                    endOffset = 4,
                    wordCount = 4,
                ),
            ),
            chapterContents = mapOf(0 to "正文内容"),
        )

        resetStoryAppState(application)

        assertNull(application.bookRepository.getBook(book.id))
    }
}
