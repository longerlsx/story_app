package com.longerlsx.storyapp.feature.reader

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.longerlsx.storyapp.MainActivity
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsEngine
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsIntentFactory
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegment
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsStartRequest
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsNotificationFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class ReaderTtsBackgroundPlaybackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val application = context.applicationContext as StoryApplication
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val engineHarness = ControlledReaderTtsEngineHarness()

    @Before
    fun setUp() {
        resetStoryAppState(context)
        grantNotificationPermissionIfNeeded()
        application.setReaderTtsEngineFactoryForTests(engineHarness::createEngine)
    }

    @After
    fun tearDown() {
        application.setReaderTtsEngineFactoryForTests(null)
        resetStoryAppState(context)
    }

    @Test
    fun backgroundPlaybackStaysActiveAndNotificationPauseResumeStayInSync() {
        val seededBook = seedBook(
            bookId = "background-book",
            title = "后台朗读测试",
            chapterTitlesToTexts = listOf(
                "第1章 开始" to """
                    第一段正文。
                    第二段正文。
                    第三段正文。
                """.trimIndent(),
            ),
        )
        application.readerSettingsStore.save(
            ReaderSettings(
                ttsSettings = ReaderTtsSettings(
                    timerPreset = ReaderTtsTimerPreset.NoTimer,
                ),
            ),
        )
        application.anchorStore.setLastOpenedBookId(seededBook.id)

        ActivityScenario.launch<MainActivity>(mainIntent()).use { _ ->
            assertTrue(device.wait(Until.hasObject(By.textContains("第一段正文")), 8_000))

            startPlaybackFor(
                bookId = seededBook.id,
                bookTitle = seededBook.title,
                chapterIndex = 0,
                charOffset = 0,
                chapterTitle = "第1章 开始",
            )
            engineHarness.awaitLatestEngine()
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)

            assertTrue(device.revealReaderChrome("停止朗读"))
            assertTrue(device.wait(Until.hasObject(By.textContains("停止朗读")), 3_000))
            assertFalse(device.hasObject(By.textContains("剩余")))

            device.pressHome()
            device.waitForIdle()
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)

            val playingNotification = waitForTtsNotification()
            assertNotificationContains(playingNotification, "朗读中")
            assertEquals(
                listOf("暂停", "停止"),
                playingNotification.actions.map { it.title.toString() },
            )

            playingNotification.actions.first { it.title.toString() == "暂停" }.actionIntent.send()
            waitForPlaybackState(ReaderTtsSessionState.PAUSED_BY_USER)

            val pausedNotification = waitForTtsNotification()
            assertEquals(
                listOf("继续", "停止"),
                pausedNotification.actions.map { it.title.toString() },
            )

            pausedNotification.actions.first { it.title.toString() == "继续" }.actionIntent.send()
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)

            val resumedNotification = waitForTtsNotification()
            resumedNotification.actions.first { it.title.toString() == "停止" }.actionIntent.send()
            waitForPlaybackState(ReaderTtsSessionState.STOPPED_BY_USER)
        }
    }

    @Test
    fun tocSelectionStopsActiveSessionAndNextStartUsesSelectedChapterTopLine() {
        val seededBook = seedBook(
            bookId = "toc-stop-book",
            title = "目录停止测试",
            chapterTitlesToTexts = listOf(
                "第1章 开始" to "第一章正文第一句。第一章正文第二句。",
                "第2章 继续" to "第二章起始句。第二章后续句。",
            ),
        )
        application.anchorStore.setLastOpenedBookId(seededBook.id)

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文第一句")), 8_000))
            startPlaybackFor(
                bookId = seededBook.id,
                bookTitle = seededBook.title,
                chapterIndex = 0,
                charOffset = 0,
                chapterTitle = "第1章 开始",
            )
            engineHarness.awaitLatestEngine()
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)

            assertTrue(device.wait(Until.hasObject(By.textContains("停止朗读")), 3_000))
            assertTrue(device.revealReaderChrome("目录", attempts = 5))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.DIRECTORY))
            val chapterTwo = device.wait(Until.findObject(By.text("第2章 继续")), 5_000)
            assertNotNull(chapterTwo)
            assertTrue(device.clickObjectCenter(chapterTwo!!))

            assertTrue(device.wait(Until.hasObject(By.textContains("第二章起始句")), 8_000))
            waitForPlaybackState(ReaderTtsSessionState.STOPPED_BY_NAVIGATION)

            assertTrue(device.revealReaderChrome("朗读"))
            val toggle = device.wait(Until.findObject(By.desc("朗读")), 3_000)
                ?: device.wait(Until.findObject(By.text("朗读")), 3_000)
            assertNotNull(toggle)
            assertTrue(device.clickObjectCenter(toggle!!))

            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            val restartedEngine = engineHarness.awaitSpokenSegment(
                chapterIndex = 1,
                startCharOffset = 0,
            )
            assertEquals(1, restartedEngine.lastSpokenSegment?.chapterIndex)
            assertEquals(0, restartedEngine.lastSpokenSegment?.startCharOffset)
        }
    }

    @Test
    fun nextChapterActionStopsActiveSessionAndRestartUsesNewChapterTopLine() {
        val seededBook = seedBook(
            bookId = "chapter-step-book",
            title = "章节切换停止测试",
            chapterTitlesToTexts = listOf(
                "第1章 开始" to "第一章第一页内容。第一章后续内容。",
                "第2章 继续" to "第二章第一页内容。第二章后续内容。",
            ),
        )
        application.readerSettingsStore.save(ReaderSettings(readingMode = com.longerlsx.storyapp.core.model.ReadingMode.PAGE))
        application.anchorStore.setLastOpenedBookId(seededBook.id)

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章第一页内容")), 8_000))
            startPlaybackFor(
                bookId = seededBook.id,
                bookTitle = seededBook.title,
                chapterIndex = 0,
                charOffset = 0,
                chapterTitle = "第1章 开始",
            )
            engineHarness.awaitLatestEngine()
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)

            assertTrue(device.revealReaderChrome("下一章"))
            assertTrue(device.tapChapterAction(previous = false))
            assertTrue(device.wait(Until.hasObject(By.textContains("第二章第一页内容")), 8_000))
            waitForPlaybackState(ReaderTtsSessionState.STOPPED_BY_NAVIGATION)

            assertTrue(device.revealReaderChrome("朗读"))
            val toggle = device.wait(Until.findObject(By.desc("朗读")), 3_000)
                ?: device.wait(Until.findObject(By.text("朗读")), 3_000)
            assertNotNull(toggle)
            assertTrue(device.clickObjectCenter(toggle!!))

            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            val restartedEngine = engineHarness.awaitSpokenSegment(
                chapterIndex = 1,
                startCharOffset = 0,
            )
            assertEquals(1, restartedEngine.lastSpokenSegment?.chapterIndex)
            assertEquals(0, restartedEngine.lastSpokenSegment?.startCharOffset)
        }
    }

    @Test
    fun openingAnotherBookStopsCurrentBookSession() {
        val firstBook = seedBook(
            bookId = "first-book",
            title = "第一本书",
            chapterTitlesToTexts = listOf(
                "第1章 开始" to "第一本书正文。",
            ),
        )
        seedBook(
            bookId = "second-book",
            title = "第二本书",
            chapterTitlesToTexts = listOf(
                "第1章 开始" to "第二本书正文。",
            ),
        )
        application.anchorStore.setLastOpenedBookId(firstBook.id)

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一本书正文")), 8_000))
            startPlaybackFor(
                bookId = firstBook.id,
                bookTitle = firstBook.title,
                chapterIndex = 0,
                charOffset = 0,
                chapterTitle = "第1章 开始",
            )
            engineHarness.awaitLatestEngine()
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)

            assertTrue(device.revealReaderChrome("返回"))
            val backButton = device.wait(Until.findObject(By.desc("返回")), 3_000)
                ?: device.wait(Until.findObject(By.text("返回")), 3_000)
            assertNotNull(backButton)
            assertTrue(device.clickObjectCenter(backButton!!))
            assertTrue(device.wait(Until.hasObject(By.text("书架")), 8_000))
            val secondBookCard = device.wait(Until.findObject(By.text("第二本书")), 5_000)
            assertNotNull(secondBookCard)
            assertTrue(device.clickObjectCenter(secondBookCard!!))
            assertTrue(device.wait(Until.hasObject(By.textContains("第二本书正文")), 8_000))
            waitForPlaybackState(ReaderTtsSessionState.STOPPED_BY_NAVIGATION)
        }
    }

    @Test
    fun stopDuringBlockedStartDoesNotResurrectPlaybackAfterInitializeCompletes() {
        val seededBook = seedBook(
            bookId = "blocked-start-book",
            title = "阻塞启动测试",
            chapterTitlesToTexts = listOf(
                "第1章 开始" to "第一段正文。第二段正文。",
            ),
        )
        application.anchorStore.setLastOpenedBookId(seededBook.id)
        engineHarness.blockNextInitialize()

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一段正文")), 8_000))

            startPlaybackFor(
                bookId = seededBook.id,
                bookTitle = seededBook.title,
                chapterIndex = 0,
                charOffset = 0,
                chapterTitle = "第1章 开始",
            )
            waitForPlaybackState(ReaderTtsSessionState.STARTING)

            application.readerTtsController.stopByUser()
            waitForPlaybackState(ReaderTtsSessionState.STOPPED_BY_USER)
            Thread.sleep(250)

            engineHarness.releaseInitialize()
            Thread.sleep(500)

            assertEquals(ReaderTtsSessionState.STOPPED_BY_USER, application.readerTtsController.playbackState)
            assertNull(engineHarness.currentEngineOrNull()?.lastSpokenSegment)
        }
    }

    @Test
    fun pauseDuringBlockedStartKeepsSessionPausedUntilExplicitResume() {
        val seededBook = seedBook(
            bookId = "blocked-pause-book",
            title = "阻塞暂停测试",
            chapterTitlesToTexts = listOf(
                "第1章 开始" to "第一段正文。第二段正文。",
            ),
        )
        application.anchorStore.setLastOpenedBookId(seededBook.id)
        engineHarness.blockNextInitialize()

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一段正文")), 8_000))

            startPlaybackFor(
                bookId = seededBook.id,
                bookTitle = seededBook.title,
                chapterIndex = 0,
                charOffset = 0,
                chapterTitle = "第1章 开始",
            )
            waitForPlaybackState(ReaderTtsSessionState.STARTING)

            context.startService(ReaderTtsIntentFactory.pauseService(context))
            waitForPlaybackState(ReaderTtsSessionState.PAUSED_BY_USER)
            Thread.sleep(250)

            engineHarness.releaseInitialize()
            Thread.sleep(500)

            assertEquals(ReaderTtsSessionState.PAUSED_BY_USER, application.readerTtsController.playbackState)
            assertNull(engineHarness.currentEngineOrNull()?.lastSpokenSegment)

            context.startService(ReaderTtsIntentFactory.resumeService(context))
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            val resumedEngine = engineHarness.awaitSpokenSegment(
                chapterIndex = 0,
                startCharOffset = 0,
            )
            assertEquals(0, resumedEngine.lastSpokenSegment?.startCharOffset)
        }
    }

    private fun seedBook(
        bookId: String,
        title: String,
        chapterTitlesToTexts: List<Pair<String, String>>,
    ): Book {
        val chapters = mutableListOf<Chapter>()
        val contents = linkedMapOf<Int, String>()
        chapterTitlesToTexts.forEachIndexed { index, (chapterTitle, chapterText) ->
            val normalizedText = chapterText.trim()
            contents[index] = normalizedText
            chapters += Chapter(
                bookId = bookId,
                chapterIndex = index,
                title = chapterTitle,
                startOffset = 0,
                endOffset = normalizedText.length,
                wordCount = normalizedText.length,
            )
        }
        val book = Book(
            id = bookId,
            title = title,
            author = "测试作者",
            importSourceType = ImportSourceType.LOCAL_FILE,
            importFileName = "$bookId.txt",
            storedPath = "/tmp/$bookId.txt",
            charset = "UTF-8",
            fileHash = bookId,
            wordCount = contents.values.sumOf(String::length),
            chapterCount = chapters.size,
            importedAt = System.currentTimeMillis(),
            lastReadAt = System.currentTimeMillis(),
        )
        runBlocking {
            application.bookRepository.saveImportedBook(
                book = book,
                chapters = chapters,
                chapterContents = contents,
            )
        }
        return book
    }

    private fun startPlaybackFor(
        bookId: String,
        bookTitle: String,
        chapterIndex: Int,
        charOffset: Int,
        chapterTitle: String,
    ) {
        val settings = application.readerSettingsStore.load().ttsSettings
        application.readerTtsController.start(
            request = ReaderTtsStartRequest(
                bookId = bookId,
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                charOffset = charOffset,
                chapterTitleOrSummary = chapterTitle,
                activeStateLabel = "朗读中",
            ),
            settings = settings,
            notificationControlsAvailable = true,
        )
    }

    private fun mainIntent(): Intent {
        return Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun waitForPlaybackState(expected: ReaderTtsSessionState) {
        val timeoutAt = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < timeoutAt) {
            if (application.readerTtsController.playbackState == expected) {
                return
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(100)
        }
        assertEquals(expected, application.readerTtsController.playbackState)
    }

    private fun waitForTtsNotification(): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        val timeoutAt = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < timeoutAt) {
            val notification = manager.activeNotifications
                .firstOrNull { it.id == ReaderTtsNotificationFactory.NOTIFICATION_ID }
                ?.notification
            if (notification != null) {
                return notification
            }
            Thread.sleep(100)
        }
        throw AssertionError("TTS notification not found")
    }

    private fun assertNotificationContains(
        notification: Notification,
        expectedText: String,
    ) {
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue((title + text).contains(expectedText))
    }

    private fun grantNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return
        }
        device.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        )
    }
}

private class ControlledReaderTtsEngineHarness {
    @Volatile
    private var latestEngine: ControlledReaderTtsEngine? = null

    private var generation = 0
    @Volatile
    private var initializeLatch: CountDownLatch? = null

    fun blockNextInitialize() {
        initializeLatch = CountDownLatch(1)
    }

    fun releaseInitialize() {
        initializeLatch?.countDown()
        initializeLatch = null
    }

    fun currentEngineOrNull(): ControlledReaderTtsEngine? = latestEngine

    fun createEngine(callback: ReaderTtsEngine.Callback): ReaderTtsEngine {
        generation += 1
        return ControlledReaderTtsEngine(
            callback = callback,
            generation = generation,
            awaitInitialize = suspend {
                val startedAt = System.currentTimeMillis()
                while (initializeLatch?.count ?: 0L > 0L) {
                    check(System.currentTimeMillis() - startedAt < 5_000) {
                        "Timed out waiting to release initialize gate"
                    }
                    delay(10)
                }
            },
        ).also { latestEngine = it }
    }

    fun awaitLatestEngine(replacing: Boolean = false): ControlledReaderTtsEngine {
        val baselineGeneration = if (replacing) latestEngine?.generation ?: 0 else 0
        val timeoutAt = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < timeoutAt) {
            val current = latestEngine
            if (current != null && current.generation > baselineGeneration) {
                return current
            }
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for fake TTS engine")
    }

    fun awaitSpokenSegment(
        chapterIndex: Int,
        startCharOffset: Int,
    ): ControlledReaderTtsEngine {
        val timeoutAt = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < timeoutAt) {
            val current = latestEngine
            val segment = current?.lastSpokenSegment
            if (
                current != null &&
                segment?.chapterIndex == chapterIndex &&
                segment.startCharOffset == startCharOffset
            ) {
                return current
            }
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for spoken segment chapter=$chapterIndex start=$startCharOffset current=${latestEngine?.lastSpokenSegment}",
        )
    }
}

private class ControlledReaderTtsEngine(
    private val callback: ReaderTtsEngine.Callback,
    val generation: Int,
    private val awaitInitialize: suspend () -> Unit,
) : ReaderTtsEngine {
    var lastSpokenSegment: ReaderTtsSegment? = null
        private set

    override suspend fun initialize(): Result<List<ReaderTtsVoiceOption>> {
        awaitInitialize()
        return Result.success(
            listOf(
                ReaderTtsVoiceOption(
                    name = "test-voice",
                    displayName = "测试语音",
                ),
            ),
        )
    }

    override fun applySettings(settings: ReaderTtsSettings) = Unit

    override fun speak(
        utteranceId: String,
        segment: ReaderTtsSegment,
    ): Boolean {
        lastSpokenSegment = segment
        callback.onUtteranceStarted(utteranceId)
        return true
    }

    override fun stop() = Unit

    override fun shutdown() = Unit
}
