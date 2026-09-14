package com.longerlsx.storyapp.feature.reader

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.data.book.BookRepository
import com.longerlsx.storyapp.data.book.FileAnchorStore
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsController
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderNavigationFailureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedChapterKeepsSavedPositionAndCanBeRetriedAfterReturning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "reader-navigation-failure-").toFile()
        val settingsStore = ReaderSettingsStore(root)
        val showReader = mutableStateOf(true)
        try {
            val bookId = "navigation-failure-book"
            val firstText = "首章唯一可读正文。"
            val secondText = "次章恢复成功正文。"
            val delegate = InMemoryBookRepository(FileAnchorStore(root))
            val failSecondChapter = AtomicBoolean(true)
            // Inject only a body-read failure; all storage, navigation and rendering remain real.
            val repository = object : BookRepository by delegate {
                override suspend fun getChapterText(bookId: String, chapterIndex: Int): String? {
                    if (chapterIndex == 1 && failSecondChapter.get()) throw IOException("第二章暂时不可读")
                    return delegate.getChapterText(bookId, chapterIndex)
                }
            }
            runBlocking {
                delegate.saveImportedBook(
                    Book(bookId, "导航失败恢复", null, ImportSourceType.LOCAL_FILE, "failure.txt",
                        root.resolve("in-memory-source.txt").path, "UTF-8", bookId,
                        firstText.length + secondText.length, 2, 0L, 0L),
                    listOf(
                        Chapter(bookId, 0, "第1章", 0, firstText.length, firstText.length),
                        Chapter(bookId, 1, "第2章", firstText.length + 1,
                            firstText.length + 1 + secondText.length, secondText.length),
                    ),
                    mapOf(0 to firstText, 1 to secondText),
                )
                settingsStore.save(ReaderSettings())
                settingsStore.awaitPendingWrites()
            }
            val controller = ReaderTtsController(launchForegroundService = { false }, sendStopCommand = {})
            composeRule.setContent {
                MaterialTheme {
                    if (showReader.value) {
                        Box(Modifier.fillMaxSize().testTag("failure-reader")) {
                            ReaderScreen(bookId, repository, settingsStore, controller, onBack = { showReader.value = false })
                        }
                    } else {
                        Text("已返回书架")
                    }
                }
            }

            fun assertSavedAnchor(anchor: ReadingAnchor) {
                assertEquals(anchor, runBlocking { delegate.getReadingProgress(bookId) }?.anchor)
                assertEquals(anchor, FileAnchorStore(root).load(bookId)?.anchor)
            }
            fun navigateToUnavailableChapter() {
                composeRule.onNodeWithTag("failure-reader").performTouchInput {
                    click(Offset(width * 0.9f, height * 0.5f))
                }
                composeRule.waitUntil(8_000) {
                    composeRule.onAllNodesWithText("第二章暂时不可读").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("第二章暂时不可读").assertIsDisplayed()
                composeRule.onNodeWithText("重试").assertIsDisplayed()
                composeRule.onNodeWithText("返回书架").assertIsDisplayed()
                assertSavedAnchor(ReadingAnchor(0, 0))
            }

            composeRule.waitUntil(8_000) {
                runBlocking { delegate.getReadingProgress(bookId) }?.anchor == ReadingAnchor(0, 0)
            }
            composeRule.onNodeWithText(firstText).assertIsDisplayed()
            navigateToUnavailableChapter()
            composeRule.onNodeWithText("返回书架").performClick()
            composeRule.waitUntil(8_000) {
                composeRule.onAllNodesWithText("已返回书架").fetchSemanticsNodes().isNotEmpty()
            }
            assertSavedAnchor(ReadingAnchor(0, 0))

            composeRule.runOnIdle { showReader.value = true }
            composeRule.waitUntil(8_000) {
                composeRule.onAllNodesWithText(firstText).fetchSemanticsNodes().isNotEmpty()
            }
            navigateToUnavailableChapter()
            failSecondChapter.set(false)
            composeRule.onNodeWithText("重试").performClick()
            composeRule.waitUntil(8_000) {
                runBlocking { delegate.getReadingProgress(bookId) }?.anchor == ReadingAnchor(1, 0)
            }
            composeRule.onNodeWithText(secondText).assertIsDisplayed()
            assertSavedAnchor(ReadingAnchor(1, 0))
        } finally {
            composeRule.runOnIdle { showReader.value = false }
            runBlocking { settingsStore.awaitPendingWrites() }
            root.deleteRecursively()
        }
    }
}
