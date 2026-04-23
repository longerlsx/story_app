package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReaderPageParagraphSpacingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pageModeParagraphSpacingChangesFirstPageVisibleParagraphs() {
        val bookId = "book-page-paragraph-spacing"
        val content = buildString {
            repeat(20) { index ->
                append("第${index + 1}段起始标记。")
                append("短句填充。")
                if (index < 19) {
                    append("\n\n")
                }
            }
        }
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to content),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        val useHighSpacing = mutableStateOf(false)
        val lowSpacingStore = createSettingsStore(
            name = "reader-page-spacing-low",
            initial = ReaderSettings(
                readingMode = ReadingMode.PAGE,
                paragraphSpacingEm = 0.4f,
            ),
        )
        val highSpacingStore = createSettingsStore(
            name = "reader-page-spacing-high",
            initial = ReaderSettings(
                readingMode = ReadingMode.PAGE,
                paragraphSpacingEm = 1.8f,
            ),
        )

        composeRule.setContent {
            androidx.compose.runtime.key(useHighSpacing.value) {
                ReaderScreen(
                    bookId = bookId,
                    repository = repository,
                    settingsStore = if (useHighSpacing.value) {
                        highSpacingStore
                    } else {
                        lowSpacingStore
                    },
                    ttsController = controller,
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("第1段起始标记。", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("第1段起始标记。", substring = true).assertIsDisplayed()
        val lowSpacingShowsSixteenthParagraph = composeRule
            .onAllNodesWithText("第16段起始标记。", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

        composeRule.runOnIdle {
            useHighSpacing.value = true
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("第1段起始标记。", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("第1段起始标记。", substring = true).assertIsDisplayed()
        val highSpacingShowsSixteenthParagraph = composeRule
            .onAllNodesWithText("第16段起始标记。", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

        assertTrue(lowSpacingShowsSixteenthParagraph)
        assertFalse(highSpacingShowsSixteenthParagraph)
    }

    private fun createRepository(
        bookId: String,
        chapterTexts: Map<Int, String>,
    ): InMemoryBookRepository {
        val repository = InMemoryBookRepository()
        runBlocking {
            repository.saveImportedBook(
                book = Book(
                    id = bookId,
                    title = "分页段落距测试",
                    author = "测试作者",
                    importSourceType = ImportSourceType.LOCAL_FILE,
                    importFileName = "test.txt",
                    storedPath = "/tmp/test.txt",
                    charset = "UTF-8",
                    fileHash = bookId,
                    wordCount = chapterTexts.values.sumOf { it.length },
                    chapterCount = chapterTexts.size,
                    importedAt = 1L,
                    lastReadAt = 1L,
                ),
                chapters = chapterTexts.entries.map { (chapterIndex, text) ->
                    Chapter(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        title = "第${chapterIndex + 1}章",
                        startOffset = 0,
                        endOffset = text.length,
                        wordCount = text.length,
                    )
                },
                chapterContents = chapterTexts,
            )
        }
        return repository
    }

    private fun createSettingsStore(
        name: String,
        initial: ReaderSettings,
    ): ReaderSettingsStore {
        val root = File(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            name,
        ).apply {
            deleteRecursively()
            mkdirs()
        }
        return ReaderSettingsStore(root).also { it.save(initial) }
    }
}
