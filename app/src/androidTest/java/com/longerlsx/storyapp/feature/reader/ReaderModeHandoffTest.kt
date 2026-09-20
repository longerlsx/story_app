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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.data.book.BookRepository
import com.longerlsx.storyapp.data.book.FileAnchorStore
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsController
import java.nio.file.Files
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderModeHandoffTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun modeAndFontSelectedDuringPendingTurnSurviveImmediateExitAndReopen() = withPendingChapter { fixture ->
        composeRule.onNodeWithTag("mode-reader").performTouchInput {
            click(Offset(width * 0.9f, height * 0.5f))
            click(Offset(width * 0.5f, height * 0.5f))
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("滚动").performClick()
        composeRule.onAllNodesWithText("A+")[0].performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("已返回书架").assertIsDisplayed()
        runBlocking { fixture.settings.awaitPendingWrites() }
        val reloaded = ReaderSettingsStore(fixture.root).load()
        assertEquals("已选模式不能因待翻页而丢失", ReadingMode.SCROLL, reloaded.readingMode)
        assertEquals(20, reloaded.fontSizeSp)
        assertEquals(ReadingAnchor(0, 0), FileAnchorStore(fixture.root).load(fixture.bookId)?.anchor)

        fixture.secondChapterReady.complete(Unit)
        composeRule.runOnIdle { fixture.showReader.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mode-reader").performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithContentDescription("阅读模式：滚动，已选中").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("字号：20").assertIsDisplayed()
    }

    @Test
    fun directoryTargetChangesToScrollBeforePageBodyIsReady() = withPendingChapter { fixture ->
        composeRule.onNodeWithTag("mode-reader").performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("目录").performClick()
        composeRule.onNodeWithText("乙").performClick()
        composeRule.onNodeWithTag("mode-reader").performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("上一章").assertIsEnabled()
        composeRule.onNodeWithContentDescription("下一章").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("滚动").performClick()
        composeRule.onNodeWithText("甲章可读正文。").assertIsDisplayed()
        org.junit.Assert.assertTrue("旧PAGE列表不能把SCROLL目录目标误判为不存在",
            composeRule.onAllNodesWithText("目标章节不存在").fetchSemanticsNodes().isEmpty())
        fixture.secondChapterReady.complete(Unit)
        composeRule.waitUntil(8_000) {
            runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.let {
                it.anchor.chapterIndex == 1 && it.readingMode == ReadingMode.SCROLL
            } == true
        }
        composeRule.onNodeWithText("乙章等待后可读正文。").assertIsDisplayed()
        org.junit.Assert.assertTrue(composeRule.onAllNodesWithText("重试").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun pendingPageTurnHandsOffWithoutSavingAnIntermediatePagePosition() = withPendingChapter { fixture ->
        selectScrollDuringPendingTurn()
        fixture.secondChapterReady.complete(Unit)
        composeRule.waitUntil(8_000) {
            fixture.writes.any { it.anchor.chapterIndex == 1 && it.readingMode == ReadingMode.SCROLL }
        }
        org.junit.Assert.assertTrue("求出锚点后不能先确认乙章PAGE再切SCROLL",
            fixture.writes.none { it.anchor.chapterIndex == 1 && it.readingMode == ReadingMode.PAGE })
        composeRule.onNodeWithText("乙章等待后可读正文。").assertIsDisplayed()
    }

    @Test
    fun selectingPageAgainBeforePendingTurnResolvesKeepsTheLatestChoice() = withPendingChapter { fixture ->
        selectScrollDuringPendingTurn()
        composeRule.onNodeWithText("翻页").performClick()
        fixture.secondChapterReady.complete(Unit)
        composeRule.waitUntil(8_000) {
            runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.let {
                it.anchor == ReadingAnchor(1, 0) && it.readingMode == ReadingMode.PAGE
            } == true
        }
        composeRule.onNodeWithContentDescription("阅读模式：翻页，已选中").assertIsDisplayed()
        assertEquals(ReadingMode.PAGE, fixture.settings.load().readingMode)
    }

    @Test
    fun returningToScrollNeverShowsItsOldChapterOrAnUnpositionedTitle() = withPendingChapter { fixture ->
        fixture.secondChapterReady.complete(Unit)
        openSettings()
        composeRule.onNodeWithText("滚动").performClick()
        composeRule.waitUntil(8_000) {
            runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.readingMode == ReadingMode.SCROLL
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithTag("mode-reader").performTouchInput {
            swipe(Offset(center.x, height * 0.75f), Offset(center.x, height * 0.25f), durationMillis = 400)
        }
        composeRule.waitUntil(8_000) {
            runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.anchor?.let {
                it.chapterIndex == 1 && it.charOffset > 0
            } == true
        }
        openSettings()
        composeRule.onNodeWithText("翻页").performClick()
        composeRule.waitUntil(8_000) {
            runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.readingMode == ReadingMode.PAGE
        }
        composeRule.onNodeWithContentDescription("目录").performClick()
        composeRule.onNodeWithText("甲").performClick()
        composeRule.waitUntil(8_000) {
            runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.anchor == ReadingAnchor(0, 0)
        }
        openSettings()
        // Finish opening the panel before taking over the clock; the transition under test starts at mode selection.
        composeRule.onNodeWithText("滚动").assertIsDisplayed()
        val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithText("滚动").performClick()
            var arrived = false
            for (frame in 0 until 30) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                org.junit.Assert.assertTrue("第${frame}帧不能闪旧乙章或先把甲章正文挤到章标题下方",
                    composeRule.onAllNodesWithText("甲章可读正文。").fetchSemanticsNodes().any {
                        it.boundsInRoot.top >= 52 * density && it.boundsInRoot.top < 90 * density
                    })
                arrived = runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.let {
                    it.anchor == ReadingAnchor(0, 0) && it.readingMode == ReadingMode.SCROLL
                } == true
                if (arrived) break
            }
            org.junit.Assert.assertTrue("目标滚动页面必须完成定位", arrived)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun selectScrollDuringPendingTurn() {
        composeRule.onNodeWithTag("mode-reader").performTouchInput {
            click(Offset(width * 0.9f, height * 0.5f))
            click(center)
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("滚动").performClick()
    }

    @Test
    fun failedModeChangeRestoresConfirmedWordAfterFontChangeWithoutDiscardingSelection() = withPendingChapter { fixture ->
        fixture.secondChapterReady.complete(Unit)
        openSettings()
        composeRule.onNodeWithText("滚动").performClick()
        composeRule.waitUntil(8_000) {
            runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.readingMode == ReadingMode.SCROLL
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithTag("mode-reader").performTouchInput {
            swipe(Offset(center.x, height * 0.75f), Offset(center.x, height * 0.25f), durationMillis = 400)
        }
        composeRule.waitUntil(8_000) {
            runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.anchor?.let {
                it.chapterIndex == 1 && it.charOffset > 0
            } == true
        }
        openSettings()
        val beforeFontChange = checkNotNull(runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }).updatedAt
        composeRule.onAllNodesWithText("A+")[0].performClick()
        composeRule.waitUntil(8_000) {
            (runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) }?.updatedAt ?: 0) > beforeFontChange
        }
        val saved = checkNotNull(runBlocking { fixture.delegate.getReadingProgress(fixture.bookId) })
        val source = fixture.texts[saved.anchor.chapterIndex]
        val paragraphStart = source.lastIndexOf('\n', saved.anchor.charOffset - 1) + 1
        val paragraphEnd = source.indexOf('\n', saved.anchor.charOffset).takeIf { it >= 0 } ?: source.length
        val savedParagraph = source.substring(paragraphStart, paragraphEnd)

        fixture.failReads.set(true)
        composeRule.onNodeWithText("翻页").performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithText("正文暂时不可读").fetchSemanticsNodes().isNotEmpty()
        }
        // The layout changes while failure is still visible. An old pixel offset would show earlier paragraphs.
        repeat(6) { composeRule.onAllNodesWithText("A+")[0].performClick() }
        val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithText(savedParagraph).fetchSemanticsNodes().any {
                it.boundsInRoot.top >= 52 * density && it.boundsInRoot.top < 90 * density
            }
        }
        composeRule.onNodeWithText("正文暂时不可读").assertIsDisplayed()
        assertEquals(saved.anchor, FileAnchorStore(fixture.root).load(fixture.bookId)?.anchor)
        runBlocking { fixture.settings.awaitPendingWrites() }
        val selected = ReaderSettingsStore(fixture.root).load()
        assertEquals(ReadingMode.PAGE, selected.readingMode)
        assertEquals(32, selected.fontSizeSp)
    }

    private fun openSettings() {
        if (composeRule.onAllNodesWithContentDescription("设置").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("mode-reader").performTouchInput { click(center) }
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
    }

    private data class Fixture(
        val root: java.io.File,
        val settings: ReaderSettingsStore,
        val delegate: InMemoryBookRepository,
        val bookId: String,
        val showReader: androidx.compose.runtime.MutableState<Boolean>,
        val secondChapterReady: CompletableDeferred<Unit>,
        val failReads: AtomicBoolean,
        val texts: List<String>,
        val writes: List<ReadingProgress>,
    )

    private fun withPendingChapter(block: (Fixture) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "reader-mode-handoff-").toFile()
        val settings = ReaderSettingsStore(root)
        val showReader = mutableStateOf(true)
        val secondChapterReady = CompletableDeferred<Unit>()
        val failReads = AtomicBoolean(false)
        val writes = CopyOnWriteArrayList<ReadingProgress>()
        val delegate = InMemoryBookRepository(FileAnchorStore(root))
        val bookId = "mode-handoff"
        val texts = listOf("甲章可读正文。", "乙章等待后可读正文。\n" + (1..80).joinToString("\n") { "乙章后续段落$it。" })
        // Delay only the real repository's body read. The complete screen, settings and progress are real.
        val repository = object : BookRepository by delegate {
            override suspend fun saveReadingProgress(progress: ReadingProgress) {
                delegate.saveReadingProgress(progress)
                writes += progress
            }

            override suspend fun getChapterText(bookId: String, chapterIndex: Int): String? {
                if (failReads.get()) throw IOException("正文暂时不可读")
                if (chapterIndex == 1) secondChapterReady.await()
                return delegate.getChapterText(bookId, chapterIndex)
            }
        }
        try {
            runBlocking {
                delegate.saveImportedBook(
                    Book(bookId, "模式交接", null, ImportSourceType.LOCAL_FILE, "mode.txt",
                        root.resolve("source.txt").path, "UTF-8", bookId,
                        texts.sumOf { it.length }, 2, 0L, 0L),
                    listOf(Chapter(bookId, 0, "甲", 0, texts[0].length, texts[0].length),
                        Chapter(bookId, 1, "乙", texts[0].length + 1,
                            texts[0].length + 1 + texts[1].length, texts[1].length)),
                    texts.mapIndexed { index, text -> index to text }.toMap(),
                )
                settings.save(ReaderSettings())
                settings.awaitPendingWrites()
            }
            val controller = ReaderTtsController(launchForegroundService = { false }, sendStopCommand = {})
            composeRule.setContent {
                MaterialTheme {
                    if (showReader.value) {
                        Box(Modifier.fillMaxSize().testTag("mode-reader")) {
                            ReaderScreen(bookId, repository, settings, controller, onBack = { showReader.value = false })
                        }
                    } else Text("已返回书架")
                }
            }
            composeRule.waitUntil(8_000) { runBlocking { delegate.getReadingProgress(bookId) }?.anchor == ReadingAnchor(0, 0) }
            block(Fixture(root, settings, delegate, bookId, showReader, secondChapterReady, failReads, texts, writes))
        } finally {
            secondChapterReady.complete(Unit)
            composeRule.runOnIdle { showReader.value = false }
            runBlocking { settings.awaitPendingWrites() }
            root.deleteRecursively()
        }
    }
}
