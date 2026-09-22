package com.longerlsx.storyapp.feature.reader

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
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
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsService
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
    fun pauseAfterRangeStartsReplaysUnfinishedSentenceInsteadOfSkippingIt() {
        val book = seedBook("range-pause", "范围暂停", listOf("第1章" to "第一句还没听完。"))
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            startPlaybackFor(book.id, book.title, 0, 0, "第1章")
            val engine = engineHarness.awaitSpokenSegment(0, 0)
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            engine.startRange(0, 4)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            context.startService(ReaderTtsIntentFactory.pauseService(context))
            waitForPlaybackState(ReaderTtsSessionState.PAUSED_BY_USER)
            assertEquals(0, application.readerTtsController.playbackSnapshot.nextRecoverableCharOffset)
            context.startService(ReaderTtsIntentFactory.resumeService(context))
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            assertEquals("第一句还没听完。", engine.lastSpokenSegment?.spokenText)
        }
    }

    @Test
    fun changingRateRepreparesUpcomingChapterWithoutRestartingTheCurrentSentence() {
        val firstSentence = "甲".repeat(39) + "。"
        val book = seedBook(
            "rate-preparation", "换速准备",
            listOf("第1章" to firstSentence + "同章下一句。", "第2章" to "邻章第一句。"),
        )
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            startPlaybackFor(book.id, book.title, 0, 0, "第1章")
            val engine = engineHarness.awaitSpokenSegment(0, 0)
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            val upcomingLocations = listOf(0 to 40, 1 to 0)
            engine.awaitPreparedWindow(1f, upcomingLocations)
            val currentUtterance = engine.activeUtteranceId

            application.readerTtsController.applySettings(ReaderTtsSettings(speechRate = 2f))
            engine.awaitPreparedWindow(2f, upcomingLocations)

            assertEquals("换速不能重播当前短句", currentUtterance, engine.activeUtteranceId)
            assertEquals(1, engine.speakCount)
            assertEquals(firstSentence, engine.lastSpokenSegment?.spokenText)
            assertEquals(1f, engine.lastSpokenSettings?.speechRate)

            engine.complete()
            engineHarness.awaitSpokenSegment(0, 40)
            assertEquals("同章下一句。", engine.lastSpokenSegment?.spokenText)
            assertEquals(2f, engine.lastSpokenSettings?.speechRate)
            engine.complete()
            engineHarness.awaitSpokenSegment(1, 0)
            assertEquals("邻章第一句。", engine.lastSpokenSegment?.spokenText)
            assertEquals(2f, engine.lastSpokenSettings?.speechRate)
            assertEquals(3, engine.speakCount)
        }
    }

    @Test
    fun resumeAfterServiceDisappearsRebuildsAtLastCompletedBoundary() {
        val book = seedBook("service-recovery", "服务恢复", listOf("第1章" to "第一句。", "第2章" to "第二句未完成。"))
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            startPlaybackFor(book.id, book.title, 0, 0, "第1章")
            val engine = engineHarness.awaitSpokenSegment(0, 0)
            engine.complete()
            engineHarness.awaitSpokenSegment(1, 0)
            context.stopService(Intent(context, com.longerlsx.storyapp.feature.reader.tts.ReaderTtsService::class.java))
            val deadline = System.currentTimeMillis() + 3_000
            while (!engine.destroyed && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue(engine.destroyed)
            waitForPlaybackState(ReaderTtsSessionState.PAUSED_BY_USER)
            context.startForegroundService(ReaderTtsIntentFactory.resumeService(context))
            val resumed = engineHarness.awaitSpokenSegment(1, 0, differentFrom = engine)
            assertEquals("第二句未完成。", resumed.lastSpokenSegment?.spokenText)
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
        }
    }

    @Test
    fun mediaSessionPauseRejectsLateCompletionAndContinuesUnfinishedText() {
        val book = seedBook("media-controls", "媒体控制", listOf("第1章" to "尚未播放完成的正文。", "第2章" to "下一章。"))
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            startPlaybackFor(book.id, book.title, 0, 0, "第1章")
            val engine = engineHarness.awaitSpokenSegment(0, 0)
            val oldCompletion = engine.completionForCurrentUtterance()
            val notification = waitForTtsNotification()
            @Suppress("DEPRECATION")
            val token = notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
            assertNotNull("Lock-screen controls require the real service's session token", token)
            val media = MediaController(context, requireNotNull(token))
            val playingUtterance = engine.activeUtteranceId
            val unexpectedStop = engine.observeNextStop()
            media.transportControls.play()
            assertFalse("Redundant PLAY must not interrupt the current utterance",
                unexpectedStop.await(1, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("Redundant PLAY must not start the same text again", playingUtterance, engine.activeUtteranceId)
            media.transportControls.pause()
            waitForPlaybackState(ReaderTtsSessionState.PAUSED_BY_USER)
            oldCompletion()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(0, runBlocking { application.listeningProgressStore.load(book.id) }?.charOffset)
            assertEquals(0, runBlocking { application.listeningProgressStore.load(book.id) }?.chapterIndex)
            media.transportControls.play()
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            assertEquals("尚未播放完成的正文。", engine.lastSpokenSegment?.spokenText)
        }
    }

    @Test
    fun mediaCustomStopEndsSessionClearsTimerAndRejectsLateCompletion() {
        val book = seedBook("media-custom-stop", "媒体停止", listOf("第1章" to "还未完成的正文。", "第2章" to "不应自行播放的后文。"))
        application.readerSettingsStore.save(ReaderSettings(ttsSettings = ReaderTtsSettings(
            timerPreset = ReaderTtsTimerPreset.Countdown(30),
        )))
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            startPlaybackFor(book.id, book.title, 0, 0, "第1章")
            val engine = engineHarness.awaitSpokenSegment(0, 0)
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            assertNotNull(application.readerTtsController.runtimeState.value.remainingTimerMillis)
            val oldCompletion = engine.completionForCurrentUtterance()
            val savedPosition = runBlocking { application.listeningProgressStore.load(book.id) }
            @Suppress("DEPRECATION")
            val token = waitForTtsNotification().extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
            val media = MediaController(context, requireNotNull(token))
            val stopAction = requireNotNull(media.playbackState).customActions.singleOrNull { it.name.toString() == "停止" }
            assertNotNull("Android 13+ 媒体卡需要 PlaybackState 提供可见的自定义停止操作", stopAction)
            media.transportControls.sendCustomAction(requireNotNull(stopAction).action, null)

            waitForPlaybackState(ReaderTtsSessionState.STOPPED_BY_USER)
            val manager = context.getSystemService(NotificationManager::class.java)
            @Suppress("DEPRECATION")
            fun serviceRunning() = context.getSystemService(ActivityManager::class.java)
                .getRunningServices(Int.MAX_VALUE).any { it.service.className == ReaderTtsService::class.java.name }
            val deadline = System.currentTimeMillis() + 3_000
            while (System.currentTimeMillis() < deadline &&
                (!engine.destroyed || serviceRunning() || manager.activeNotifications.any { it.id == ReaderTtsNotificationFactory.NOTIFICATION_ID })) {
                Thread.sleep(20)
            }
            assertTrue("停止必须销毁播放引擎", engine.destroyed)
            assertFalse("停止必须结束服务", serviceRunning())
            assertFalse(manager.activeNotifications.any { it.id == ReaderTtsNotificationFactory.NOTIFICATION_ID })
            assertNull(application.readerTtsController.runtimeState.value.remainingTimerMillis)
            assertEquals(ReaderTtsTimerPreset.Countdown(30), application.readerSettingsStore.load().ttsSettings.timerPreset)

            oldCompletion()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(ReaderTtsSessionState.STOPPED_BY_USER, application.readerTtsController.playbackState)
            assertEquals("旧完成不能推进队列", 1, engine.speakCount)
            assertEquals(savedPosition, runBlocking { application.listeningProgressStore.load(book.id) })
        }
    }

    @Test
    fun previewUsesSameEngineKeepsBookPausedAndNeverWritesItsTextAsListeningProgress() {
        val book = seedBook("preview-checkpoint", "试听续听", listOf("第1章" to "这段正文还没有听完。"))
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            startPlaybackFor(book.id, book.title, 0, 0, "第1章")
            val engine = engineHarness.awaitSpokenSegment(0, 0)
            val before = runBlocking { application.listeningProgressStore.load(book.id) }
            application.readerTtsController.requestVoicePreview(ReaderTtsSettings())
            val deadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < deadline &&
                (!application.readerTtsController.runtimeState.value.isVoicePreviewing || engine.lastSpokenSegment?.spokenText == "这段正文还没有听完。")) {
                Thread.sleep(20)
            }
            assertTrue(application.readerTtsController.runtimeState.value.isVoicePreviewing)
            assertTrue(engine === engineHarness.currentEngineOrNull())
            assertFalse(engine.lastSpokenSegment?.spokenText == "这段正文还没有听完。")
            engine.complete()
            val finishDeadline = System.currentTimeMillis() + 3_000
            while (application.readerTtsController.runtimeState.value.isVoicePreviewing && System.currentTimeMillis() < finishDeadline) Thread.sleep(20)
            assertFalse(application.readerTtsController.runtimeState.value.isVoicePreviewing)
            assertEquals(ReaderTtsSessionState.PAUSED_BY_USER, application.readerTtsController.playbackState)
            assertEquals(before, runBlocking { application.listeningProgressStore.load(book.id) })
            application.readerTtsController.requestResumePlayback()
            waitForPlaybackState(ReaderTtsSessionState.PLAYING)
            assertEquals("这段正文还没有听完。", engine.lastSpokenSegment?.spokenText)
        }
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

            assertTrue(device.revealReaderChrome("听书"))
            assertTrue(device.wait(Until.hasObject(By.desc("暂停朗读")), 3_000))
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
        differentFrom: ControlledReaderTtsEngine? = null,
    ): ControlledReaderTtsEngine {
        val timeoutAt = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < timeoutAt) {
            val current = latestEngine
            val segment = current?.lastSpokenSegment
            if (
                current != null &&
                current !== differentFrom &&
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
    @Volatile var destroyed: Boolean = false
        private set
    @Volatile var activeUtteranceId: String? = null
        private set
    @Volatile private var stopObserver: CountDownLatch? = null
    @Volatile var lastSpokenSegment: ReaderTtsSegment? = null
        private set
    @Volatile private var settings = ReaderTtsSettings()
    @Volatile var lastSpokenSettings: ReaderTtsSettings? = null
        private set
    @Volatile var speakCount = 0
        private set
    @Volatile private var preparedWindow: Pair<ReaderTtsSettings, List<Pair<Int, Int>>>? = null

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

    override fun applySettings(settings: ReaderTtsSettings) {
        this.settings = settings
    }

    override fun prepareUpcoming(segments: List<ReaderTtsSegment>) {
        preparedWindow = settings to segments.map { it.chapterIndex to it.startCharOffset }
    }

    fun awaitPreparedWindow(speechRate: Float, locations: List<Pair<Int, Int>>) {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            val prepared = preparedWindow
            if (prepared?.first?.speechRate == speechRate && prepared.second == locations) return
            Thread.sleep(20)
        }
        assertEquals("后续声音应按最新语速准备", speechRate, preparedWindow?.first?.speechRate)
        assertEquals("准备顺序应覆盖同章后句及邻章首句", locations, preparedWindow?.second)
    }

    override fun speak(
        utteranceId: String,
        segment: ReaderTtsSegment,
    ): Boolean {
        activeUtteranceId = utteranceId
        lastSpokenSettings = settings
        speakCount++
        lastSpokenSegment = segment
        callback.onUtteranceStarted(utteranceId)
        return true
    }

    override fun stop() { stopObserver?.countDown() }

    fun observeNextStop(): CountDownLatch = CountDownLatch(1).also { stopObserver = it }

    fun startRange(start: Int, end: Int) {
        callback.onUtteranceRangeStart(requireNotNull(activeUtteranceId), start, end)
    }

    fun complete() {
        callback.onUtteranceCompleted(requireNotNull(activeUtteranceId))
    }

    fun completionForCurrentUtterance(): () -> Unit {
        val id = requireNotNull(activeUtteranceId)
        return { callback.onUtteranceCompleted(id) }
    }

    override fun shutdown() { destroyed = true }
}
