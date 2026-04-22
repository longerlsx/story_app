package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset

class ReaderTtsControllerTest {

    @Test
    fun startMovesIntoStartingStateAndCapturesBookContextWhenServiceLaunchSucceeds() {
        val launched = mutableListOf<ReaderTtsStartRequest>()
        val controller = ReaderTtsController(
            launchForegroundService = {
                launched += it
                true
            },
            sendStopCommand = {},
        )
        val request = ReaderTtsStartRequest(
            bookId = "book-1",
            bookTitle = "白夜",
            chapterIndex = 3,
            charOffset = 120,
            chapterTitleOrSummary = "第三章",
            activeStateLabel = "朗读中",
        )

        val result = controller.start(
            request = request,
            settings = ReaderTtsSettings(
                voiceName = "robot-voice",
                speechRate = 1.15f,
                pitch = 0.92f,
                timerPreset = ReaderTtsTimerPreset.Countdown(30),
            ),
            notificationControlsAvailable = false,
        )

        assertTrue(result)
        assertEquals(listOf(request), launched)
        assertEquals(ReaderTtsSessionState.STARTING, controller.playbackState)
        assertEquals("book-1", controller.currentBookId)
        assertEquals("白夜", controller.currentBookTitle)
        assertEquals("第三章", controller.currentPlaybackSummary)
        assertEquals(ReaderTtsTimerPreset.Countdown(30), controller.timerPreset)
        assertEquals(30 * 60_000L, controller.remainingTimerMillis)
        assertEquals("robot-voice", controller.selectedVoiceName)
        assertFalse(controller.notificationControlsAvailable)
        assertNull(controller.localErrorMessage)
        assertEquals(
            "通知权限未开启，后台仍可朗读，但通知栏控制可能不可用",
            controller.localStatusMessage,
        )
    }

    @Test
    fun startFailsWhenForegroundServiceLaunchFails() {
        val controller = ReaderTtsController(
            launchForegroundService = { false },
            sendStopCommand = {},
        )

        val result = controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-2",
                bookTitle = "雾都",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "正文",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(),
        )

        assertFalse(result)
        assertEquals(ReaderTtsSessionState.FAILED, controller.playbackState)
        assertEquals("无法启动朗读服务", controller.localErrorMessage)
    }

    @Test
    fun pauseAndResumeByUserKeepSessionContext() {
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-3",
                bookTitle = "山海经",
                chapterIndex = 2,
                charOffset = 88,
                chapterTitleOrSummary = "第二章",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(),
        )
        controller.onPlaybackStarted()

        controller.pauseByUser()
        controller.resumeFromPause()

        assertEquals(ReaderTtsSessionState.PLAYING, controller.playbackState)
        assertEquals("book-3", controller.currentBookId)
        assertEquals("第二章", controller.currentPlaybackSummary)
    }

    @Test
    fun pauseCallsAreAcceptedWhilePlaybackIsStillStarting() {
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-starting",
                bookTitle = "启动中",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(),
        )

        controller.pauseByUser()

        assertEquals(ReaderTtsSessionState.PAUSED_BY_USER, controller.playbackState)
        controller.resumeFromPause()
        assertEquals(ReaderTtsSessionState.PLAYING, controller.playbackState)
    }

    @Test
    fun stopByNavigationEndsCurrentSessionAndDispatchesStopCommand() {
        var stopCommands = 0
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = { stopCommands++ },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-4",
                bookTitle = "寒夜",
                chapterIndex = 5,
                charOffset = 10,
                chapterTitleOrSummary = "第五章",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(),
        )
        controller.onPlaybackStarted()

        controller.stopByNavigation("已停止朗读，重新开始将从当前位置开始")

        assertEquals(ReaderTtsSessionState.STOPPED_BY_NAVIGATION, controller.playbackState)
        assertEquals("book-4", controller.currentBookId)
        assertEquals(1, stopCommands)
        assertEquals("已停止朗读，重新开始将从当前位置开始", controller.localStatusMessage)
    }

    @Test
    fun duplicateStartIsRejectedWhileSessionIsActive() {
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-5",
                bookTitle = "围城",
                chapterIndex = 1,
                charOffset = 20,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(),
        )
        controller.onPlaybackStarted()

        val result = controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-6",
                bookTitle = "活着",
                chapterIndex = 2,
                charOffset = 0,
                chapterTitleOrSummary = "第二章",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(),
        )

        assertFalse(result)
        assertEquals(ReaderTtsSessionState.PLAYING, controller.playbackState)
        assertEquals("book-5", controller.currentBookId)
    }

    @Test
    fun pauseAndStopCallsAreIgnoredWhenNoSessionIsActive() {
        var stopCommands = 0
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = { stopCommands++ },
        )

        controller.pauseByUser()
        controller.pauseByAudioFocus()
        controller.stopByUser()
        controller.stopByNavigation()

        assertEquals(ReaderTtsSessionState.OFF, controller.playbackState)
        assertNull(controller.currentBookId)
        assertEquals(0, stopCommands)
    }

    @Test
    fun externalStopMarksSessionStoppedWithoutDispatchingAnotherStopCommand() {
        var stopCommands = 0
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = { stopCommands++ },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-stop",
                bookTitle = "通知栏停止",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(),
        )
        controller.onPlaybackStarted()

        controller.markStoppedByUser()

        assertEquals(ReaderTtsSessionState.STOPPED_BY_USER, controller.playbackState)
        assertEquals(0, stopCommands)
    }

    @Test
    fun applySettingsUpdatesRuntimeStateAndDispatchesLiveSettingsWhileSessionIsActive() {
        val sentSettings = mutableListOf<ReaderTtsSettings>()
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
            sendSettingsCommand = { sentSettings += it },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-7",
                bookTitle = "朝花夕拾",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(),
        )
        controller.onPlaybackStarted()

        val nextSettings = ReaderTtsSettings(
            voiceName = "voice-b",
            speechRate = 1.2f,
            pitch = 0.85f,
            timerPreset = ReaderTtsTimerPreset.Countdown(15),
        )

        controller.applySettings(nextSettings)

        assertEquals("voice-b", controller.selectedVoiceName)
        assertEquals(ReaderTtsTimerPreset.Countdown(15), controller.timerPreset)
        assertEquals(15 * 60_000L, controller.remainingTimerMillis)
        assertEquals(listOf(nextSettings), sentSettings)
    }

    @Test
    fun applySettingsPersistsRuntimePreferencesWithoutCreatingLiveTimerWhenNoSessionIsActive() {
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
            sendSettingsCommand = {},
        )

        controller.applySettings(
            ReaderTtsSettings(
                voiceName = "voice-c",
                speechRate = 0.95f,
                pitch = 1.05f,
                timerPreset = ReaderTtsTimerPreset.Countdown(90),
            ),
        )

        assertEquals("voice-c", controller.selectedVoiceName)
        assertEquals(ReaderTtsTimerPreset.Countdown(90), controller.timerPreset)
        assertNull(controller.remainingTimerMillis)
    }

    @Test
    fun restartFromLocationKeepsTimerBudgetAndDispatchesRestartCommand() {
        val restartRequests = mutableListOf<ReaderTtsStartRequest>()
        val controller = ReaderTtsController(
            launchForegroundService = { true },
            sendStopCommand = {},
            sendRestartCommand = { restartRequests += it },
        )
        controller.start(
            request = ReaderTtsStartRequest(
                bookId = "book-8",
                bookTitle = "骆驼祥子",
                chapterIndex = 0,
                charOffset = 0,
                chapterTitleOrSummary = "第一章",
                activeStateLabel = "朗读中",
            ),
            settings = ReaderTtsSettings(
                timerPreset = ReaderTtsTimerPreset.Countdown(30),
            ),
        )
        controller.onPlaybackStarted()
        controller.updateRemainingTimerMillis(12 * 60_000L)

        val restartRequest = ReaderTtsStartRequest(
            bookId = "book-8",
            bookTitle = "骆驼祥子",
            chapterIndex = 1,
            charOffset = 240,
            chapterTitleOrSummary = "第二章",
            activeStateLabel = "朗读中",
        )

        controller.restartFromLocation(restartRequest)

        assertEquals(ReaderTtsSessionState.STARTING, controller.playbackState)
        assertEquals("第二章", controller.currentPlaybackSummary)
        assertEquals(12 * 60_000L, controller.remainingTimerMillis)
        assertEquals(listOf(restartRequest), restartRequests)
    }
}
