package com.longerlsx.storyapp.feature.reader
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsController
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsPlaybackSnapshot
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegment
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsStartRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReaderTtsFollowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressRestartDispatchesRestartAtPressedBodyOffsetWhenTtsIsActive() {
        val bookId = "book-long-press"
        val text = """
            第一段正文
            第二段正文
            第三段正文
        """.trimIndent()
        val restartRequests = mutableListOf<ReaderTtsStartRequest>()
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to text),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-follow-long-press",
            initial = ReaderSettings(readingMode = ReadingMode.SCROLL),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
            sendRestartCommand = { restartRequests += it },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "长按重启测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("第二段正文").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("第二段正文")
            .assertIsDisplayed()
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("从这里重新朗读").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(1, restartRequests.size)
            val request = restartRequests.single()
            assertEquals(0, request.chapterIndex)
            assertTrue(request.charOffset >= text.indexOf("第二段正文"))
            assertTrue(request.charOffset < text.indexOf("第二段正文") + "第二段正文".length)
        }
    }

    @Test
    fun pageModeLongPressRestartMapsVisibleTextBackToRawChapterOffset() {
        val bookId = "book-page-long-press"
        val text = "\n\n第二段命中正文。第三句。"
        val restartRequests = mutableListOf<ReaderTtsStartRequest>()
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to text),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-follow-page-long-press",
            initial = ReaderSettings(readingMode = ReadingMode.PAGE),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
            sendRestartCommand = { restartRequests += it },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "分页长按重启测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("第二段命中正文。第三句。").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("第二段命中正文。第三句。")
            .assertIsDisplayed()
            .performTouchInput { longClick() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            restartRequests.size == 1
        }

        composeRule.runOnIdle {
            assertEquals(1, restartRequests.size)
            val request = restartRequests.single()
            assertEquals(0, request.chapterIndex)
            assertTrue(request.charOffset >= text.indexOf("第二段命中正文"))
            assertTrue(request.charOffset < text.indexOf("第二段命中正文") + "第二段命中正文".length)
        }
    }

    @Test
    fun pageModeLongPressOnLaterParagraphDispatchesRestartNearPressedParagraph() {
        val bookId = "book-page-long-press-later-paragraph"
        val firstParagraph = "第一页第一段正文，用于占住当前朗读位置。"
        val secondParagraph = "第一页第二段正文，长按这里应该切到这一段。"
        val thirdParagraph = "第一页第三段正文，用于保证同页多段命中。"
        val text = listOf(firstParagraph, secondParagraph, thirdParagraph).joinToString("\n")
        val restartRequests = mutableListOf<ReaderTtsStartRequest>()
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to text),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-follow-page-long-press-later-paragraph",
            initial = ReaderSettings(readingMode = ReadingMode.PAGE),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
            sendRestartCommand = { restartRequests += it },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "分页长按后段测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(secondParagraph).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(secondParagraph)
            .assertIsDisplayed()
            .performTouchInput { longClick() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            restartRequests.size == 1
        }

        composeRule.runOnIdle {
            val request = restartRequests.single()
            assertEquals(0, request.chapterIndex)
            assertTrue(
                "Expected restart charOffset to land in second paragraph, but was ${request.charOffset}",
                request.charOffset >= text.indexOf(secondParagraph),
            )
            assertTrue(
                "Expected restart charOffset to stay within second paragraph, but was ${request.charOffset}",
                request.charOffset < text.indexOf(secondParagraph) + secondParagraph.length,
            )
        }
    }

    @Test
    fun tappingBodyWhileTtsIsActiveAndSettingsExpandedReturnsToReadingOnly() {
        val bookId = "book-tts-settings-body-tap"
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(
                0 to """
                    第一段正文。
                    第二段正文。
                    第三段正文。
                """.trimIndent(),
            ),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-settings-body-tap",
            initial = ReaderSettings(readingMode = ReadingMode.SCROLL),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "正文点击返回沉浸态测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("当前状态：朗读中").assertIsDisplayed()

        composeRule.onNodeWithText("第二段正文。").assertIsDisplayed().performClick()

        composeRule.onNodeWithContentDescription("沉浸式阅读头部").assertIsDisplayed()
    }

    @Test
    fun pageModeFollowsPlaybackIntoSpokenChapter() {
        val bookId = "book-page-follow"
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(
                0 to "第一章当前页内容。",
                1 to "第二章命中内容。",
            ),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-follow-page",
            initial = ReaderSettings(readingMode = ReadingMode.PAGE),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "分页跟随测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()
        controller.updatePlaybackSnapshot(
            ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(
                    chapterIndex = 1,
                    startCharOffset = 0,
                    endCharOffset = 7,
                    spokenText = "第二章命中内容。",
                ),
            ),
        )

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("第二章命中内容。").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("第二章命中内容。").assertIsDisplayed()
    }

    @Test
    fun pageSwipeSuppressesFollowUntilLaterPlaybackUpdatesArrive() {
        val bookId = "book-page-swipe-suppression"
        val content = buildString {
            append("第一页起点。\n")
            repeat(10) { index ->
                append("用于制造多页效果的铺垫正文第${index}行，这是一段足够长的测试文本。\n")
            }
            append("第二页标记。\n")
            repeat(8) { index ->
                append("第二页之后的延展正文第${index}行，这是一段足够长的测试文本。\n")
            }
        }
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to content),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-follow-page-swipe",
            initial = ReaderSettings(
                readingMode = ReadingMode.PAGE,
                paragraphSpacingEm = 0.4f,
            ),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "分页滑动抑制测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()
        controller.updatePlaybackSnapshot(
            ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(
                    chapterIndex = 0,
                    startCharOffset = 0,
                    endCharOffset = 18,
                    spokenText = "第一页起点。",
                ),
            ),
        )

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("第一页起点。", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(
                    "用于制造多页效果的铺垫正文第0行，这是一段足够长的测试文本。",
                    substring = true,
                ).fetchSemanticsNodes().isNotEmpty()
        }
        repeat(20) {
            if (composeRule.onAllNodesWithText("第二页标记。", substring = true).fetchSemanticsNodes().isNotEmpty()) {
                return@repeat
            }
            composeRule.onRoot().performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("第二页标记。", substring = true).assertIsDisplayed()

        controller.updatePlaybackSnapshot(
            ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(
                    chapterIndex = 0,
                    startCharOffset = 0,
                    endCharOffset = 18,
                    spokenText = "第一页起点。",
                ),
                lastConfirmedSpokenRange = com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange(2, 4),
            ),
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("第二页标记。", substring = true).assertIsDisplayed()
    }

    @Test
    fun pausedSessionToggleResumesInsteadOfStopping() {
        val bookId = "book-resume-toggle"
        var stopCommands = 0
        var resumeCommands = 0
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to "第一段正文。第二段正文。"),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-resume-toggle",
            initial = ReaderSettings(readingMode = ReadingMode.SCROLL),
        )
        lateinit var controller: ReaderTtsController
        controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = { stopCommands += 1 },
            sendResumeCommand = {
                resumeCommands += 1
                controller.resumeFromPause()
            },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "暂停恢复测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()
        controller.pauseByUser()

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("继续朗读").performClick()

        composeRule.runOnIdle {
            assertEquals(1, resumeCommands)
            assertEquals(0, stopCommands)
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PLAYING, controller.playbackState)
        }
    }

    @Test
    fun pausedSessionImmersiveActionResumesInsteadOfStopping() {
        val bookId = "book-immersive-resume"
        var stopCommands = 0
        var resumeCommands = 0
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to "第一段正文。第二段正文。"),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-immersive-resume",
            initial = ReaderSettings(readingMode = ReadingMode.SCROLL),
        )
        lateinit var controller: ReaderTtsController
        controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = { stopCommands += 1 },
            sendResumeCommand = {
                resumeCommands += 1
                controller.resumeFromPause()
            },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "沉浸态暂停恢复测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()
        controller.pauseByUser()

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.onNodeWithText("继续朗读").performClick()

        composeRule.runOnIdle {
            assertEquals(1, resumeCommands)
            assertEquals(0, stopCommands)
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PLAYING, controller.playbackState)
        }
    }

    @Test
    fun pageBoundaryTurnStopsActiveTtsBeforeOpeningAdjacentChapter() {
        val bookId = "book-page-boundary-stop"
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(
                0 to "第一章当前页。",
                1 to "第二章新页。",
            ),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-page-boundary-stop",
            initial = ReaderSettings(readingMode = ReadingMode.PAGE),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "分页边界停播测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()

        composeRule.setContent {
            ReaderScreen(
                bookId = bookId,
                repository = repository,
                settingsStore = settingsStore,
                ttsController = controller,
                onBack = {},
            )
        }

        composeRule.onNodeWithText("第一章当前页。").assertIsDisplayed()
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            click(
                androidx.compose.ui.geometry.Offset(
                    rootBounds.width * 0.92f,
                    rootBounds.height * 0.5f,
                ),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("第二章新页。").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("第二章新页。").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.STOPPED_BY_NAVIGATION, controller.playbackState)
        }
    }

    @Test
    fun disposingReaderClearsCurrentBookTransientStatusMessage() {
        val bookId = "book-transient-clear"
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to "第一段正文。"),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-transient-clear",
            initial = ReaderSettings(readingMode = ReadingMode.SCROLL),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "瞬时消息清理测试",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = settingsStore.load().ttsSettings,
        )
        controller.onPlaybackStarted()
        controller.stopByNavigation("已停止朗读，重新开始将从当前位置开始")

        val showReader = androidx.compose.runtime.mutableStateOf(true)
        composeRule.setContent {
            if (showReader.value) {
                ReaderScreen(
                    bookId = bookId,
                    repository = repository,
                    settingsStore = settingsStore,
                    ttsController = controller,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("已停止朗读，重新开始将从当前位置开始").assertIsDisplayed()
        composeRule.runOnIdle {
            showReader.value = false
        }

        composeRule.runOnIdle {
            assertNull(controller.localStatusMessage)
        }
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
                    title = "测试书籍",
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
