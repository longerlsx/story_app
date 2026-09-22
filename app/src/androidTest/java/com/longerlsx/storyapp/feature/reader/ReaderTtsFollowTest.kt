package com.longerlsx.storyapp.feature.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsController
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsPlaybackSnapshot
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegment
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsStartRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReaderTtsFollowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newPageListeningStartsAtRenderedFirstLineInsteadOfRestoredMidPageAnchor() {
        grantNotificationPermission()
        val bookId = "book-start-page-top"
        val prefix = "页顶从这里开始。"
        val text = prefix + "山风吹过石桥，沿路的树影渐渐移到河岸。".repeat(100)
        val restoredOffset = 80
        val repository = createRepository(bookId, mapOf(0 to text))
        runBlocking {
            repository.saveReadingProgress(ReadingProgress(bookId, ReadingAnchor(0, restoredOffset), ReadingMode.PAGE, 2L))
        }
        val settings = createSettingsStore("tts-start-page-top", ReaderSettings(readingMode = ReadingMode.PAGE))
        val starts = mutableListOf<ReaderTtsStartRequest>()
        val controller = ReaderTtsController(launchForegroundService = { starts += it; true }, sendStopCommand = {})
        composeRule.setContent {
            ReaderScreen(bookId, repository, settings, controller, onBack = {})
        }
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithText(prefix, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(prefix, substring = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("Fixture must restore beyond the first rendered line", restoredOffset > layout.getLineEnd(0))
        assertTrue("The restored text must still be on this rendered page", restoredOffset < layout.layoutInput.text.length)
        assertEquals(0, layout.getLineStart(0))

        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("朗读").performClick()
        composeRule.waitUntil(5_000) { starts.size == 1 }
        composeRule.runOnIdle {
            assertEquals("Fresh start must use the displayed page top, not its restoration target", 0, starts.single().charOffset)
            assertEquals(0, starts.single().chapterIndex)
        }
    }

    @Test
    fun scrollingStartsAtActualTopLineAndStopReopenUsesNewTopWhilePauseOnlyResumes() {
        grantNotificationPermission()
        val bookId = "book-start-scroll-top"
        // One long paragraph makes returning to the paragraph/chapter start an observable error.
        val text = (0 until 120).joinToString("") { "第${it}处山风经过石桥，树影沿着河岸慢慢移动。" }
        val repository = createRepository(bookId, mapOf(0 to text))
        val settings = createSettingsStore("tts-start-scroll-top", ReaderSettings(
            readingMode = ReadingMode.SCROLL,
            ttsSettings = ReaderTtsSettings(timerPreset = ReaderTtsTimerPreset.Countdown(30)),
        ))
        val starts = mutableListOf<ReaderTtsStartRequest>()
        val restarts = mutableListOf<ReaderTtsStartRequest>()
        var resumes = 0
        lateinit var controller: ReaderTtsController
        controller = ReaderTtsController(
            launchForegroundService = { starts += it; true },
            sendStopCommand = {},
            sendPauseCommand = { controller.pauseByUser() },
            sendResumeCommand = { resumes++; controller.resumeFromPause() },
            sendRestartCommand = { restarts += it },
        )
        composeRule.setContent { ReaderScreen(bookId, repository, settings, controller, onBack = {}) }
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        fun scrollForward() {
            composeRule.onAllNodes(hasScrollAction())[0].performTouchInput {
                swipe(
                    start = androidx.compose.ui.geometry.Offset(centerX, height * 0.75f),
                    end = androidx.compose.ui.geometry.Offset(centerX, height * 0.35f),
                    durationMillis = 600,
                )
            }
            composeRule.waitForIdle()
        }

        fun openAndStartListening() {
            if (composeRule.onAllNodesWithContentDescription("朗读").fetchSemanticsNodes().isEmpty()) {
                composeRule.onRoot().performTouchInput { click(center) }
            }
            composeRule.onNodeWithContentDescription("朗读").performClick()
        }

        fun renderedTopLineStart(): Int {
            val body = composeRule.onNodeWithText(text)
            val layouts = mutableListOf<TextLayoutResult>()
            body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val bodyTop = body.fetchSemanticsNode().positionInRoot.y
            val scrollBounds = composeRule.onAllNodes(hasScrollAction())[0].fetchSemanticsNode().boundsInRoot
            // The actual list begins below the header; its readable area has the existing 8dp inset.
            val readableTop = scrollBounds.top + with(composeRule.density) { 8.dp.toPx() }
            val line = (0 until layout.lineCount).first { bodyTop + layout.getLineBottom(it) > readableTop }
            assertTrue("Fixture must place the viewport inside the long paragraph", line > 0)
            return layout.getLineStart(line)
        }

        scrollForward()
        val firstTop = renderedTopLineStart()
        openAndStartListening()
        composeRule.waitUntil(5_000) { starts.size == 1 }
        composeRule.runOnIdle {
            assertEquals("New listening must begin at the actual rendered top line", firstTop, starts.single().charOffset)
            controller.onPlaybackStarted()
            controller.updateRemainingTimerMillis(42_000L)
        }
        composeRule.onNodeWithContentDescription("暂停朗读").performClick()
        composeRule.onNodeWithContentDescription("继续朗读").performClick()
        composeRule.runOnIdle {
            assertEquals(1, resumes)
            assertEquals("Resume must not launch a fresh page-top session", 1, starts.size)
            assertTrue("Resume must not dispatch a restart at another position", restarts.isEmpty())
            assertEquals(42_000L, controller.remainingTimerMillis)
        }

        composeRule.onNodeWithContentDescription("停止朗读").performClick()
        composeRule.onNodeWithContentDescription("收起听书面板").performClick()
        scrollForward()
        val secondTop = renderedTopLineStart()
        assertTrue("The second start must exercise a different visible line", secondTop > firstTop)
        openAndStartListening()
        composeRule.waitUntil(5_000) { starts.size == 2 }
        composeRule.runOnIdle {
            assertEquals("Stop then start must use the newly visible line, not the old listening point", secondTop, starts.last().charOffset)
            assertEquals(0, starts.last().chapterIndex)
        }
    }

    @Test
    fun failedPlaybackRetryAfterPanelCollapsePreservesRecoverableOffset() {
        grantNotificationPermission()
        val bookId = "book-failed-retry-panel"
        val prefix = "页首正文与失败恢复位置不同。"
        val text = prefix + "山风吹过石桥，树影沿着河岸慢慢移动。".repeat(8)
        val repository = createRepository(bookId, mapOf(0 to text))
        val settings = createSettingsStore("tts-failed-retry-panel", ReaderSettings(readingMode = ReadingMode.PAGE))
        val starts = mutableListOf<ReaderTtsStartRequest>()
        val controller = ReaderTtsController(launchForegroundService = { starts += it; true }, sendStopCommand = {})
        val error = "这一段声音准备失败，请重试。"
        composeRule.setContent {
            ReaderScreen(bookId, repository, settings, controller, onBack = {})
        }
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithText(prefix, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(prefix, substring = true).assertIsDisplayed()
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.onNodeWithContentDescription("朗读").performClick()
        composeRule.waitUntil(5_000) { starts.size == 1 }
        composeRule.runOnIdle {
            assertEquals("Fresh listening begins at this page's first line", 0, starts.single().charOffset)
            controller.onPlaybackStarted()
            controller.updatePlaybackSnapshot(ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(0, 40, 120, text.substring(40, 120)),
                lastConfirmedSpokenRange = ReaderTtsCharacterRange(40, 76),
                nextRecoverableCharOffset = 76,
                nextRecoverableRange = ReaderTtsCharacterRange(76, 120),
            ))
            controller.handleStartupFailure(error)
        }
        composeRule.onNodeWithText(error).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("收起听书面板").performClick()
        composeRule.onNodeWithText(error).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.FAILED, controller.playbackState)
            assertEquals(error, controller.localErrorMessage)
            assertEquals(76, controller.playbackSnapshot.nextRecoverableCharOffset)
            assertEquals("Closing the panel must not restart playback", 1, starts.size)
        }
        composeRule.onNodeWithContentDescription("展开听书面板").performClick()
        composeRule.onNodeWithText(error).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("重试朗读").performClick()
        composeRule.waitUntil(5_000) { starts.size == 2 }
        composeRule.runOnIdle {
            assertEquals(0, starts.last().chapterIndex)
            assertEquals("Retry must recover the failed sentence, not restart from the page top", 76, starts.last().charOffset)
        }
    }

    @Test
    fun pausedNotificationOpensListeningTextWhileOrdinaryOpeningKeepsReadingPosition() {
        val bookId = "book-paused-notification"
        val repository = createRepository(bookId, mapOf(0 to "普通打开保留阅读位置。", 1 to "通知应定位暂停听到的正文。"))
        val settings = createSettingsStore("tts-paused-notification", ReaderSettings(readingMode = ReadingMode.PAGE))
        val controller = ReaderTtsController(launchForegroundService = { true }, sendStopCommand = {})
        val request = ReaderTtsStartRequest(bookId, "通知定位", 1, 0, "第2章", "朗读中")
        controller.start(request, settings.load().ttsSettings)
        controller.onPlaybackStarted()
        controller.pauseByUser()
        controller.updatePlaybackSnapshot(ReaderTtsPlaybackSnapshot(currentSegment = ReaderTtsSegment(1, 0, 13, "通知应定位暂停听到的正文。")))
        val notification = mutableStateOf<ReaderTtsStartRequest?>(null)
        composeRule.setContent {
            ReaderScreen(
                bookId, repository, settings, controller, onBack = {},
                listeningOpenRequest = notification.value,
                onListeningOpenHandled = { notification.value = null },
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("普通打开保留阅读位置。", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("普通打开保留阅读位置。", substring = true).assertIsDisplayed()
        composeRule.runOnIdle { notification.value = request }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("通知应定位暂停听到的正文。", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("通知应定位暂停听到的正文。", substring = true).assertIsDisplayed()
        composeRule.runOnIdle {
            assertNull(notification.value)
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PAUSED_BY_USER, controller.playbackState)
        }
    }

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
        composeRule.waitUntil(timeoutMillis = 5_000) { restartRequests.size == 1 }
        composeRule.onNodeWithText("从这里重新朗读").assertDoesNotExist()

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
    fun tappingBodyWhileTtsIsActiveAndListeningExpandedReturnsToReadingOnlyWithoutStopping() {
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

        composeRule.onNodeWithContentDescription("展开听书面板").performClick()
        composeRule.onNodeWithText("定时关闭").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("暂停朗读").assertIsDisplayed()

        val paragraphBounds = composeRule.onNodeWithText("第二段正文。").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(centerX, paragraphBounds.center.y - rootBounds.top))
        }

        composeRule.onNodeWithText("定时关闭").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("沉浸式阅读头部").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("暂停朗读").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PLAYING, controller.playbackState)
        }
    }

    @Test
    fun tappingBodyWhileTtsIsActiveAndReadingOnlyRevealsChrome() {
        val bookId = "book-tts-reading-only-body-tap"
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
            name = "reader-tts-reading-only-body-tap",
            initial = ReaderSettings(readingMode = ReadingMode.SCROLL),
        )
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = "正文点击唤出操作层测试",
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

        composeRule.onNodeWithContentDescription("沉浸式阅读头部").assertIsDisplayed()
        val paragraphBounds = composeRule.onNodeWithText("第二段正文。").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(centerX, paragraphBounds.center.y - rootBounds.top))
        }
        composeRule.onNodeWithContentDescription("设置").assertIsDisplayed()
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
        composeRule.runOnIdle {
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PLAYING, controller.playbackState)
        }
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
    fun pageModeDoesNotJumpBackWhenCurrentSegmentCompletesAfterCrossPageFollow() {
        val bookId = "book-page-follow-completion"
        val marker = "第二页标记。"
        val content = buildString {
            append("第一页起点。\n")
            repeat(10) { index ->
                append("用于制造多页效果的铺垫正文第${index}行，这是一段足够长的测试文本。\n")
            }
            append(marker)
            append('\n')
            repeat(8) { index ->
                append("第二页之后的延展正文第${index}行，这是一段足够长的测试文本。\n")
            }
        }
        val markerStart = content.indexOf(marker)
        val markerEnd = markerStart + marker.length
        val repository = createRepository(
            bookId = bookId,
            chapterTexts = mapOf(0 to content),
        )
        val settingsStore = createSettingsStore(
            name = "reader-tts-follow-page-completion",
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
                bookTitle = "分页跟随完成态测试",
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
            composeRule.onAllNodesWithText("第一页起点。", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        controller.updatePlaybackSnapshot(
            ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(
                    chapterIndex = 0,
                    startCharOffset = 0,
                    endCharOffset = markerEnd,
                    spokenText = content.substring(0, markerEnd),
                ),
                lastConfirmedSpokenRange = ReaderTtsCharacterRange(markerStart, markerEnd),
                nextRecoverableCharOffset = markerEnd,
                nextRecoverableRange = ReaderTtsCharacterRange(markerEnd, markerEnd),
            ),
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(marker, substring = true).assertIsDisplayed()

        controller.updatePlaybackSnapshot(
            ReaderTtsPlaybackSnapshot(
                currentSegment = ReaderTtsSegment(
                    chapterIndex = 0,
                    startCharOffset = 0,
                    endCharOffset = markerEnd,
                    spokenText = content.substring(0, markerEnd),
                ),
                lastConfirmedSpokenRange = ReaderTtsCharacterRange(markerEnd, markerEnd),
                nextRecoverableCharOffset = markerEnd,
                nextRecoverableRange = ReaderTtsCharacterRange(markerEnd, markerEnd),
            ),
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText(marker, substring = true).assertIsDisplayed()
    }

    @Test
    fun pausedSessionToggleResumesInsteadOfStopping() {
        val bookId = "book-resume-toggle"
        var stopCommands = 0
        var pauseCommands = 0
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
            sendPauseCommand = {
                pauseCommands += 1
                controller.pauseByUser()
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
        composeRule.onNodeWithContentDescription("暂停朗读").performClick()
        composeRule.runOnIdle {
            assertEquals(1, pauseCommands)
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PAUSED_BY_USER, controller.playbackState)
        }
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

        composeRule.onNodeWithContentDescription("继续朗读").performClick()

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

        composeRule.onNodeWithText("已停止朗读，重新开始将从当前位置开始").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.STOPPED_BY_NAVIGATION, controller.playbackState)
            assertEquals("已停止朗读，重新开始将从当前位置开始", controller.localStatusMessage)
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

    private fun grantNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            androidx.test.uiautomator.UiDevice.getInstance(instrumentation).executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} android.permission.POST_NOTIFICATIONS",
            )
        }
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
