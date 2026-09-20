package com.longerlsx.storyapp.feature.reader

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.longerlsx.storyapp.MainActivity
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ListeningProgress
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsNotificationFactory
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real engine, AudioTrack, Service and reader UI. Host audio capture supplies sound-content evidence. */
@RunWith(AndroidJUnit4::class)
class ReaderOfflineListeningTest {
    private val application = ApplicationProvider.getApplicationContext<StoryApplication>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val controller get() = application.readerTtsController

    @Before
    fun setUp() {
        resetStoryAppState(application)
        application.setReaderTtsEngineFactoryForTests(null)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            device.executeShellCommand("pm grant ${application.packageName} android.permission.POST_NOTIFICATIONS")
        }
        device.wakeUp()
        device.pressMenu()
    }

    @After
    fun tearDown() {
        controller.stopByUser()
        device.wakeUp()
        device.pressMenu()
        resetStoryAppState(application)
    }

    @Test
    fun normalReaderPlaysAcrossChaptersAndScreenOffNotificationPauseResumeReturnsToListeningText() {
        // A normal 36-character unit followed by an 8-character chapter tail. The next
        // chapter must be prepared while the longer unit plays, not only during the tail.
        val firstChapter = "清晨，他推开窗，沿着河岸慢慢前行。路旁的树叶带着露水，远处有人打开木门。商量前面的路线。"
        val book = seedBook("offline-flow", listOf(firstChapter, chapterBody(2), chapterBody(3)))
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            startFromReader(book)
            awaitCondition("actual AudioTrack playback", 60_000) {
                controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
            }
            awaitCondition("real playback crosses the first chapter", 60_000) { progress(book.id)?.chapterIndex == 1 }
            val beforeScreenOff = requireNotNull(progress(book.id)).position()
            device.sleep()
            assertFalse(device.isScreenOn)
            awaitCondition("completed speech advances while screen is off", 60_000) {
                progress(book.id)?.position()?.let { it != beforeScreenOff } == true && audioManager.isMusicActive
            }
            assertFalse(device.isScreenOn)
            sendNotificationAction("暂停")
            awaitCondition("notification pauses actual output", 5_000) {
                controller.playbackState == ReaderTtsSessionState.PAUSED_BY_USER && !audioManager.isMusicActive
            }
            val paused = progress(book.id)
            Thread.sleep(1_000)
            assertEquals(paused, progress(book.id))
            sendNotificationAction("继续")
            awaitCondition("notification resumes actual output while screen stays off", 60_000) {
                controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
            }
            assertFalse(device.isScreenOn)
            awaitCondition("resumed playback completes new text", 60_000) {
                progress(book.id)?.position()?.let { it != paused?.position() } == true
            }
            sendNotificationAction("暂停")
            awaitCondition("freeze listening position for notification navigation", 5_000) {
                controller.playbackState == ReaderTtsSessionState.PAUSED_BY_USER
            }
            val chapterTitle = requireNotNull(controller.currentPlaybackSummary)
            device.wakeUp()
            device.pressMenu()
            ttsNotification().contentIntent.send()
            awaitCondition("notification opens the listening chapter", 8_000) {
                device.hasObject(By.desc("沉浸式章节：$chapterTitle")) ||
                    device.hasObject(By.desc("顶部栏标题：${book.title} $chapterTitle"))
            }
            assertEquals(book.id, controller.currentBookId)
            Log.i(TAG, "shortFlow=passed realAudio=true screenOffCrossChapter=true notificationControls=true")
        }
    }

    @Test
    fun notificationDuckResumesTheSameAudioWithoutGeneratingOrReplayingTheSentence() {
        val firstText = "清晨，他推开窗，看见远处的青山。河边的人慢慢走过小桥，准备开始新的一天。"
        val book = seedBook("offline-notification-duck", listOf(firstText, "他放下书，安静地等待。"))
        val marker = "notificationDuckBegin=${SystemClock.elapsedRealtime()}"
        val notificationFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener({}, Handler(Looper.getMainLooper()))
            .build()
        try {
            ActivityScenario.launch<MainActivity>(mainIntent()).use {
                Log.i(TAG, marker)
                awaitReader(book)
                selectThirtyMinuteTimerThroughSettings()
                startFromReader(book)
                awaitCondition("real speech before notification duck", 60_000) {
                    controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
                }
                Thread.sleep(650)
                assertEquals(0, controller.playbackSnapshot.currentSegment?.chapterIndex)
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(notificationFocus))
                awaitCondition("notification pauses actual AudioTrack", 5_000) {
                    controller.playbackState == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS && !audioManager.isMusicActive
                }
                val pausedPosition = progress(book.id)?.position()
                val pausedBudget = controller.runtimeState.value.remainingTimerMillis
                assertTrue(requireNotNull(pausedBudget) > 0)
                Thread.sleep(1_200)
                assertEquals(pausedPosition, progress(book.id)?.position())
                assertEquals(pausedBudget, controller.runtimeState.value.remainingTimerMillis)
                val gainAt = SystemClock.elapsedRealtime()
                audioManager.abandonAudioFocusRequest(notificationFocus)
                awaitCondition("notification returns to real speech", 60_000) {
                    controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
                }
                fun playbackEvents(): String {
                    val logs = device.executeShellCommand("logcat -d -v brief -s OfflineReaderTts:I ReaderOfflineListening:I")
                    assertTrue("The start marker must still be in the device log", logs.contains(marker))
                    return logs.substringAfter(marker)
                }
                var events = playbackEvents()
                assertEquals("The interrupted first sentence must be synthesized only once", 1,
                    events.lineSequence().count { it.contains("synthesisStart chapter=0 offset=0 ") })
                awaitCondition("the retained AudioTrack actually advances again", 2_000) {
                    events = playbackEvents()
                    events.contains("focusResumed ")
                }
                // Same-session frame evidence comes from the real AudioTrack, never a fake engine.
                val paused = Regex("focusPaused utterance=(\\S+) session=(\\d+) frame=(\\d+)").find(events)
                val resumed = Regex("focusResumed utterance=(\\S+) session=(\\d+) frame=(\\d+) elapsedRealtime=(\\d+)").find(events)
                assertTrue("A playing AudioTrack must retain its pause point", paused != null && resumed != null)
                assertEquals(requireNotNull(paused).groupValues[1], requireNotNull(resumed).groupValues[1])
                assertEquals(paused.groupValues[2], resumed.groupValues[2])
                assertTrue(paused.groupValues[3].toLong() > 0)
                assertTrue("Playback must advance beyond its paused frame", resumed.groupValues[3].toLong() > paused.groupValues[3].toLong())
                val observedResumeMillis = resumed.groupValues[4].toLong() - gainAt
                assertTrue("Returning notification focus must not wait for synthesis: $observedResumeMillis ms",
                    observedResumeMillis in 0 until 1_000)
                awaitCondition("resumed first sentence completes and crosses the chapter", 30_000) {
                    progress(book.id)?.chapterIndex == 1
                }
                Log.i(TAG, "notificationDuck=passed observedResumeMs=$observedResumeMillis session=${resumed.groupValues[2]} pausedFrame=${paused.groupValues[3]} resumedFrame=${resumed.groupValues[3]}")
            }
        } finally {
            audioManager.abandonAudioFocusRequest(notificationFocus)
        }
    }

    @Test
    fun notificationDuringSynthesisHoldsCompletedAudioUntilFocusReturnsWithoutRegenerating() {
        val text = "清晨，他推开窗，看见远处的青山。河边的人慢慢走过小桥，准备开始新的一天。"
        val book = seedBook("offline-focus-during-synthesis", listOf(text))
        val marker = "generationFocusBegin=${SystemClock.elapsedRealtime()}"
        val notificationFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener({}, Handler(Looper.getMainLooper()))
            .build()
        fun events(): String = device.executeShellCommand("logcat -d -v brief -s OfflineReaderTts:I ReaderOfflineListening:I")
            .also { assertTrue("The test marker must remain available", it.contains(marker)) }
            .substringAfter(marker)
        try {
            ActivityScenario.launch<MainActivity>(mainIntent()).use {
                Log.i(TAG, marker)
                startFromReader(book)
                awaitCondition("the current sentence has entered real synthesis", 60_000) {
                    events().contains("synthesisStart chapter=0 offset=0 ")
                }
                assertEquals(ReaderTtsSessionState.STARTING, controller.playbackState)
                assertFalse(audioManager.isMusicActive)
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(notificationFocus))
                awaitCondition("generation is retained under temporary focus loss", 5_000) {
                    controller.playbackState == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS
                }
                val pausedPosition = progress(book.id)?.position()
                awaitCondition("real synthesis completes while focus remains elsewhere", 60_000) {
                    assertEquals(ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS, controller.playbackState)
                    assertFalse("Generated audio must not start before focus returns", audioManager.isMusicActive)
                    assertEquals(pausedPosition, progress(book.id)?.position())
                    events().contains("synthesisMs=")
                }
                Thread.sleep(300)
                assertEquals(ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS, controller.playbackState)
                assertFalse(audioManager.isMusicActive)
                assertEquals(pausedPosition, progress(book.id)?.position())
                audioManager.abandonAudioFocusRequest(notificationFocus)
                awaitCondition("retained generated audio actually begins", 5_000) {
                    controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
                }
                assertEquals("Focus recovery must consume the already completed synthesis", 1,
                    events().lineSequence().count { it.contains("synthesisStart chapter=0 offset=0 ") })
                awaitCondition("the retained sentence completes through the real Service", 30_000) {
                    progress(book.id)?.completed == true && controller.playbackState == ReaderTtsSessionState.STOPPED_AT_BOOK_END
                }
                assertEquals(text.length, requireNotNull(progress(book.id)).charOffset)
                Log.i(TAG, "generationFocus=passed generatedOnce=true silentUntilGain=true completed=true")
            }
        } finally {
            audioManager.abandonAudioFocusRequest(notificationFocus)
        }
    }

    @Test
    fun realAudioFocusResumesOnlyAfterTransientLossWithoutAnExplicitUserPause() {
        val text = "清晨，他推开窗，看见远处的青山。河边的人慢慢走过小桥，准备开始新的一天。\n".repeat(12)
        val book = seedBook("offline-audio-focus", listOf(text))
        // A distinct listener creates a real competing focus client, without replacing the engine
        // or calling ReaderTtsAudioFocusManager.onFocusChange ourselves.
        val competingListener = object : AudioManager.OnAudioFocusChangeListener {
            override fun onAudioFocusChange(focusChange: Int) = Unit
        }
        fun competingRequest(gain: Int) = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(competingListener, Handler(Looper.getMainLooper()))
            .build()
        val transientFocus = competingRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        val permanentFocus = competingRequest(AudioManager.AUDIOFOCUS_GAIN)
        fun assertRemainsPaused(expected: ReaderTtsSessionState) {
            val pausedPosition = progress(book.id)?.position()
            // Observe the state as well as output: an unwanted restart can still be generating
            // its first audio, so a silent AudioTrack alone would be a false pass.
            repeat(20) {
                Thread.sleep(100)
                assertEquals("Releasing focus must not override this pause", expected, controller.playbackState)
                assertFalse("Paused listening must not emit audio", audioManager.isMusicActive)
            }
            assertEquals(pausedPosition, progress(book.id)?.position())
        }
        try {
            ActivityScenario.launch<MainActivity>(mainIntent()).use {
                startFromReader(book)
                awaitCondition("real speech before competing focus", 60_000) {
                    controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
                }
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(transientFocus))
                awaitCondition("platform transient focus loss pauses speech", 5_000) {
                    controller.playbackState == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS && !audioManager.isMusicActive
                }
                val beforeAutomaticResume = progress(book.id)?.position()
                audioManager.abandonAudioFocusRequest(transientFocus)
                awaitCondition("platform focus gain automatically resumes speech", 60_000) {
                    controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
                }
                awaitCondition("automatically resumed speech completes text", 60_000) {
                    progress(book.id)?.position()?.let { it != beforeAutomaticResume } == true
                }

                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(transientFocus))
                awaitCondition("second platform interruption pauses speech", 5_000) {
                    controller.playbackState == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS && !audioManager.isMusicActive
                }
                @Suppress("DEPRECATION")
                val sessionToken = ttsNotification().extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                MediaController(application, requireNotNull(sessionToken)).transportControls.pause()
                awaitCondition("explicit media pause takes precedence over focus recovery", 5_000) {
                    controller.playbackState == ReaderTtsSessionState.PAUSED_BY_USER
                }
                audioManager.abandonAudioFocusRequest(transientFocus)
                assertRemainsPaused(ReaderTtsSessionState.PAUSED_BY_USER)
                sendNotificationAction("继续")
                awaitCondition("user resumes after choosing to stay paused", 60_000) {
                    controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
                }

                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(permanentFocus))
                awaitCondition("platform permanent focus loss pauses speech", 5_000) {
                    controller.playbackState == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS && !audioManager.isMusicActive
                }
                val beforeExplicitResume = progress(book.id)?.position()
                audioManager.abandonAudioFocusRequest(permanentFocus)
                assertRemainsPaused(ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS)
                sendNotificationAction("继续")
                awaitCondition("only explicit play resumes after permanent loss", 60_000) {
                    controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
                }
                awaitCondition("explicitly resumed speech completes text", 60_000) {
                    progress(book.id)?.position()?.let { it != beforeExplicitResume } == true
                }
                Log.i(TAG, "platformAudioFocus=passed transientResume=true userPauseRespected=true permanentLossRequiresPlay=true")
            }
        } finally {
            audioManager.abandonAudioFocusRequest(transientFocus)
            audioManager.abandonAudioFocusRequest(permanentFocus)
        }
    }

    @Test
    fun thirtyMinuteTimerKeepsRealOfflinePlaybackAdvancingWithScreenOffUntilItStops() {
        assumeTrue("Explicit long run: -e runLongListening true", InstrumentationRegistry.getArguments().getString("runLongListening") == "true")
        val book = seedBook("offline-thirty-minutes", List(40) { chapterBody(it + 1) })
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            awaitReader(book)
            selectThirtyMinuteTimerThroughSettings()
            startFromReader(book)
            awaitCondition("first real speech starts", 60_000) {
                controller.playbackState == ReaderTtsSessionState.PLAYING && audioManager.isMusicActive
            }
            val startedAt = SystemClock.elapsedRealtime()
            assertEquals(ReaderTtsTimerPreset.Countdown(30), controller.timerPreset)
            device.sleep()
            assertFalse(device.isScreenOn)
            Log.i(TAG, "phase=screenOffStart elapsedRealtime=$startedAt timerMinutes=30")
            var lastProgress = requireNotNull(progress(book.id)).position()
            var lastAdvanceAt = startedAt
            var lastProgressCheckAt = startedAt
            var inactiveSince: Long? = null
            var maximumObservedInactiveMillis = 0L
            var audioActiveSamples = 0
            var nextReportAt = startedAt + 5 * 60_000
            val endDeadline = startedAt + 30 * 60_000 + 15_000
            while (controller.playbackState != ReaderTtsSessionState.STOPPED_BY_TIMER && SystemClock.elapsedRealtime() < endDeadline) {
                val now = SystemClock.elapsedRealtime()
                val state = controller.playbackState
                if (state == ReaderTtsSessionState.STOPPED_BY_TIMER) break
                assertFalse("Long listening must not wake the display", device.isScreenOn)
                assertTrue("Unexpected listening terminal state: ${controller.runtimeState.value}",
                    state == ReaderTtsSessionState.STARTING || state == ReaderTtsSessionState.PLAYING)
                if (audioManager.isMusicActive) {
                    audioActiveSamples++
                    inactiveSince?.let { maximumObservedInactiveMillis = maxOf(maximumObservedInactiveMillis, now - it) }
                    inactiveSince = null
                } else {
                    if (inactiveSince == null) inactiveSince = now
                    val inactiveFor = now - requireNotNull(inactiveSince)
                    maximumObservedInactiveMillis = maxOf(maximumObservedInactiveMillis, inactiveFor)
                    assertTrue("No AudioTrack activity for 30 seconds", inactiveFor < 30_000)
                }
                if (now - lastProgressCheckAt >= 30_000) {
                    val current = requireNotNull(progress(book.id)).position()
                    if (current != lastProgress) {
                        lastProgress = current
                        lastAdvanceAt = now
                    }
                    assertTrue("Completed speech has not advanced for two minutes", now - lastAdvanceAt < 120_000)
                    lastProgressCheckAt = now
                }
                if (now >= nextReportAt) {
                    Log.i(TAG, "phase=screenOffMinutes${(now - startedAt) / 60_000} position=$lastProgress maxObservedInactiveMs=$maximumObservedInactiveMillis")
                    nextReportAt += 5 * 60_000
                }
                Thread.sleep(2_000)
            }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            assertEquals(ReaderTtsSessionState.STOPPED_BY_TIMER, controller.playbackState)
            assertTrue("Timer fired before thirty actual minutes: $elapsed ms", elapsed >= 30 * 60_000 - 2_000)
            assertTrue("Real audio must have remained active across the sampling period", audioActiveSamples > 0)
            assertTrue("The test input must still have unread text at timer expiry", progress(book.id)?.completed == false)
            awaitCondition("timer stops actual audio", 5_000) { !audioManager.isMusicActive }
            assertFalse(device.isScreenOn)
            Log.i(TAG, "phase=timerStopped elapsedMs=$elapsed position=${progress(book.id)?.position()} activeSamples=$audioActiveSamples maxObservedInactiveMs=$maximumObservedInactiveMillis samplingMillis=2000")
        }
    }

    private fun awaitReader(book: Book) {
        awaitCondition("reader opens the seeded first chapter", 10_000) {
            device.hasObject(By.desc("沉浸式章节：第1章 清晨")) ||
                device.hasObject(By.desc("顶部栏标题：${book.title} 第1章 清晨"))
        }
    }

    private fun startFromReader(book: Book) {
        awaitReader(book)
        assertTrue(device.revealReaderChrome("朗读"))
        assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.TTS))
        device.wait(Until.findObject(By.text("从当前文字开始")), 300)?.let { device.clickObjectCenter(it) }
    }

    private fun selectThirtyMinuteTimerThroughSettings() {
        assertTrue(device.revealReaderChrome("设置"))
        assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))
        val tab = device.wait(Until.findObject(By.descStartsWith("设置分页：朗读，")), 3_000)
        assertTrue("Settings must expose its reading-aloud tab", tab != null)
        assertTrue(device.clickObjectCenter(requireNotNull(tab)))
        var timer = device.findObject(By.text("30 分钟"))
        repeat(5) {
            if (timer == null) {
                val settingsScroll = device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.top }
                checkNotNull(settingsScroll) { "Scrollable voice settings were not visible" }
                settingsScroll.scroll(Direction.DOWN, 0.7f)
                timer = device.wait(Until.findObject(By.text("30 分钟")), 500)
            }
        }
        assertTrue("The 30-minute option must be reachable through real settings", timer != null)
        assertTrue(device.clickObjectCenter(requireNotNull(timer)))
        awaitCondition("timer choice is saved", 3_000) {
            application.readerSettingsStore.load().ttsSettings.timerPreset == ReaderTtsTimerPreset.Countdown(30)
        }
        device.pressBack()
    }

    private fun sendNotificationAction(label: String) {
        awaitCondition("notification action $label", 5_000) {
            ttsNotificationOrNull()?.actions?.any { it.title.toString() == label } == true
        }
        ttsNotification().actions.first { it.title.toString() == label }.actionIntent.send()
    }

    private fun ttsNotificationOrNull(): Notification? = application.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.id == ReaderTtsNotificationFactory.NOTIFICATION_ID }?.notification

    private fun ttsNotification(): Notification = requireNotNull(ttsNotificationOrNull())
    private fun progress(bookId: String): ListeningProgress? = runBlocking { application.listeningProgressStore.load(bookId) }
    private fun ListeningProgress.position(): Pair<Int, Int> = chapterIndex to charOffset

    private fun awaitCondition(label: String, timeoutMillis: Long, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            check(controller.playbackState != ReaderTtsSessionState.FAILED) { "$label: ${controller.localErrorMessage}" }
            Thread.sleep(50)
        }
        throw AssertionError("Timed out: $label; state=${controller.runtimeState.value}")
    }

    private fun mainIntent() = Intent(application, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun chapterBody(number: Int): String = buildString {
        repeat(20) { paragraph ->
            append("第${number}段旅程里，他在清晨整理行囊，沿着河岸慢慢前行。路旁的树叶带着露水，远处有人推开木门，准备开始新的一天。\n")
            append("同行的人记下第${paragraph + 1}处风景，聊起昨天读过的故事。大家约好走到桥边再停下来，喝一口水，继续商量前面的路线。\n")
        }
    }

    private fun seedBook(id: String, texts: List<String>): Book {
        val chapters = texts.mapIndexed { index, text -> Chapter(id, index, "第${index + 1}章 清晨", 0, text.length, text.length) }
        val now = System.currentTimeMillis()
        val book = Book(id, "离线听书实测", "自写测试正文", ImportSourceType.LOCAL_FILE, "$id.txt", "/test/$id.txt",
            "UTF-8", id, texts.sumOf(String::length), chapters.size, now, now)
        runBlocking { application.bookRepository.saveImportedBook(book, chapters, texts.mapIndexed { index, text -> index to text }.toMap()) }
        application.anchorStore.setLastOpenedBookId(id)
        return book
    }

    private companion object { const val TAG = "ReaderOfflineListening" }
}
