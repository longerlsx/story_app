package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ReaderTtsController(
    private val launchForegroundService: (ReaderTtsStartRequest) -> Boolean,
    private val sendStopCommand: () -> Unit,
    private val sendResumeCommand: () -> Unit = {},
    private val sendSettingsCommand: (ReaderTtsSettings) -> Unit = {},
    private val sendRestartCommand: (ReaderTtsStartRequest) -> Unit = {},
) {
    private val runtime = MutableStateFlow(ReaderTtsRuntimeState())

    val runtimeState: StateFlow<ReaderTtsRuntimeState> = runtime.asStateFlow()

    val playbackState: ReaderTtsSessionState
        get() = runtime.value.playbackState

    val currentBookId: String?
        get() = runtime.value.currentBookId

    val currentBookTitle: String?
        get() = runtime.value.currentBookTitle

    val currentPlaybackSummary: String?
        get() = runtime.value.currentPlaybackSummary

    val localErrorMessage: String?
        get() = runtime.value.localErrorMessage

    val localStatusMessage: String?
        get() = runtime.value.localStatusMessage

    val playbackSnapshot: ReaderTtsPlaybackSnapshot
        get() = runtime.value.playbackSnapshot

    val timerPreset: ReaderTtsTimerPreset
        get() = runtime.value.timerPreset

    val remainingTimerMillis: Long?
        get() = runtime.value.remainingTimerMillis

    val selectedVoiceName: String?
        get() = runtime.value.selectedVoiceName

    val notificationControlsAvailable: Boolean
        get() = runtime.value.notificationControlsAvailable

    fun start(
        request: ReaderTtsStartRequest,
        settings: ReaderTtsSettings,
        notificationControlsAvailable: Boolean = true,
    ): Boolean {
        if (playbackState.isOngoingSession()) {
            runtime.update {
                it.copy(localErrorMessage = "朗读已在进行中")
            }
            return false
        }

        runtime.value = ReaderTtsRuntimeState(
            playbackState = ReaderTtsSessionState.OFF,
            currentBookId = request.bookId,
            currentBookTitle = request.bookTitle,
            currentPlaybackSummary = request.chapterTitleOrSummary,
            activeStateLabel = request.activeStateLabel,
            localStatusMessage = if (notificationControlsAvailable) {
                null
            } else {
                NOTIFICATION_PERMISSION_DEGRADED_MESSAGE
            },
            playbackSnapshot = ReaderTtsPlaybackSnapshot(),
            timerPreset = settings.timerPreset,
            remainingTimerMillis = settings.timerPreset.toInitialTimerMillis(),
            selectedVoiceName = settings.voiceName,
            speechRate = settings.speechRate,
            pitch = settings.pitch,
            notificationControlsAvailable = notificationControlsAvailable,
        )

        if (!launchForegroundService(request)) {
            runtime.update {
                it.copy(
                    playbackState = ReaderTtsSessionState.FAILED,
                    localErrorMessage = "无法启动朗读服务",
                    remainingTimerMillis = null,
                    playbackSnapshot = ReaderTtsPlaybackSnapshot(),
                )
            }
            return false
        }

        runtime.update {
            it.copy(playbackState = ReaderTtsSessionState.STARTING)
        }
        return true
    }

    fun onPlaybackStarted() {
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.PLAYING,
                localErrorMessage = null,
            )
        }
    }

    fun pauseByUser() {
        if (!playbackState.isSpeakingSession()) {
            return
        }
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.PAUSED_BY_USER,
                playbackSnapshot = it.playbackSnapshot.copy(activePauseReason = ReaderTtsPauseReason.USER),
            )
        }
    }

    fun pauseByAudioFocus() {
        if (!playbackState.isSpeakingSession()) {
            return
        }
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS,
                playbackSnapshot = it.playbackSnapshot.copy(activePauseReason = ReaderTtsPauseReason.AUDIO_FOCUS),
            )
        }
    }

    fun resumeFromPause() {
        if (playbackState.isPausedSession()) {
            runtime.update {
                it.copy(
                    playbackState = ReaderTtsSessionState.PLAYING,
                    playbackSnapshot = it.playbackSnapshot.copy(activePauseReason = null),
                )
            }
        }
    }

    fun requestResumePlayback() {
        if (!playbackState.isPausedSession()) {
            return
        }
        sendResumeCommand()
    }

    fun stopByUser() {
        if (!playbackState.isOngoingSession()) {
            return
        }
        sendStopCommand()
        markStoppedByUser()
    }

    fun markStoppedByUser() {
        if (!playbackState.isOngoingSession()) {
            return
        }
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.STOPPED_BY_USER,
                playbackSnapshot = it.playbackSnapshot.copy(activePauseReason = null),
            )
        }
    }

    fun restartFromLocation(request: ReaderTtsStartRequest) {
        if (!playbackState.isOngoingSession()) {
            return
        }
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.STARTING,
                currentBookId = request.bookId,
                currentBookTitle = request.bookTitle,
                currentPlaybackSummary = request.chapterTitleOrSummary,
                activeStateLabel = request.activeStateLabel,
                localErrorMessage = null,
                localStatusMessage = null,
                playbackSnapshot = ReaderTtsPlaybackSnapshot(),
            )
        }
        sendRestartCommand(request)
    }

    fun stopByNavigation(localMessage: String? = null) {
        if (!playbackState.isOngoingSession()) {
            return
        }
        sendStopCommand()
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.STOPPED_BY_NAVIGATION,
                localStatusMessage = localMessage,
                playbackSnapshot = it.playbackSnapshot.copy(activePauseReason = null),
            )
        }
    }

    fun stopByTimer() {
        if (!playbackState.isOngoingSession()) {
            return
        }
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.STOPPED_BY_TIMER,
                playbackSnapshot = it.playbackSnapshot.copy(activePauseReason = null),
            )
        }
    }

    fun stopAtBookEnd() {
        if (!playbackState.isOngoingSession()) {
            return
        }
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.STOPPED_AT_BOOK_END,
                playbackSnapshot = it.playbackSnapshot.copy(activePauseReason = null),
            )
        }
    }

    fun handleStartupFailure(message: String) {
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.FAILED,
                localErrorMessage = message,
                remainingTimerMillis = null,
                playbackSnapshot = ReaderTtsPlaybackSnapshot(),
            )
        }
    }

    fun updatePlaybackSnapshot(snapshot: ReaderTtsPlaybackSnapshot) {
        runtime.update {
            it.copy(playbackSnapshot = snapshot)
        }
    }

    fun updateRemainingTimerMillis(remainingTimerMillis: Long?) {
        runtime.update {
            it.copy(remainingTimerMillis = remainingTimerMillis)
        }
    }

    fun updateAvailableVoices(voices: List<ReaderTtsVoiceOption>) {
        runtime.update {
            it.copy(
                availableVoices = voices,
                availableVoicesLoaded = true,
            )
        }
    }

    fun updateAvailableVoicesResult(result: Result<List<ReaderTtsVoiceOption>>) {
        result.getOrNull()?.let(::updateAvailableVoices)
    }

    fun applySettings(settings: ReaderTtsSettings) {
        val nextRemainingTimerMillis = when {
            !playbackState.isOngoingSession() -> null
            runtime.value.timerPreset != settings.timerPreset -> settings.timerPreset.toInitialTimerMillis()
            else -> runtime.value.remainingTimerMillis
        }
        runtime.update {
            it.copy(
                timerPreset = settings.timerPreset,
                remainingTimerMillis = nextRemainingTimerMillis,
                selectedVoiceName = settings.voiceName,
                speechRate = settings.speechRate,
                pitch = settings.pitch,
            )
        }
        if (playbackState.isOngoingSession()) {
            sendSettingsCommand(settings)
        }
    }

    fun updatePlaybackSummary(summary: String) {
        runtime.update {
            it.copy(currentPlaybackSummary = summary)
        }
    }

    fun clearLocalStatusMessage() {
        runtime.update {
            it.copy(localStatusMessage = null)
        }
    }

    fun clearLocalErrorMessage() {
        runtime.update {
            it.copy(localErrorMessage = null)
        }
    }

    fun resetForTests() {
        runtime.value = ReaderTtsRuntimeState()
    }

    companion object {
        const val NOTIFICATION_PERMISSION_DEGRADED_MESSAGE =
            "通知权限未开启，后台仍可朗读，但通知栏控制可能不可用"
    }
}
