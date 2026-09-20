package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import com.longerlsx.storyapp.core.model.ListeningProgress
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
    private val sendPauseCommand: () -> Unit = {},
    private val sendVoicePreviewCommand: (ReaderTtsSettings) -> Unit = {},
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
        if (!playbackState.isOngoingSession()) {
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
        if (playbackState.isSpeakingSession() || runtime.value.isVoicePreviewing) {
            return
        }
        dispatchCommand("无法继续朗读，请重试", sendResumeCommand)
    }

    fun requestPausePlayback() {
        if (playbackState.isOngoingSession() || runtime.value.isVoicePreviewing) {
            dispatchCommand("无法暂停朗读，请重试", sendPauseCommand)
        }
    }

    fun requestVoicePreview(settings: ReaderTtsSettings) {
        dispatchCommand("无法开始试听，请重试") { sendVoicePreviewCommand(settings) }
    }

    fun stopByUser() {
        if (!playbackState.isOngoingSession() && !runtime.value.isVoicePreviewing) {
            return
        }
        if (dispatchCommand("无法停止朗读，请重试", sendStopCommand)) markStoppedByUser()
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
        dispatchCommand("无法重新定位朗读，请重试") { sendRestartCommand(request) }
    }

    fun stopByNavigation(localMessage: String? = null) {
        if (!playbackState.isOngoingSession()) {
            return
        }
        if (!dispatchCommand("无法停止朗读，请重试", sendStopCommand)) return
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
                isVoicePreviewing = false,
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
            dispatchCommand("声音设置暂未生效，请重试") { sendSettingsCommand(settings) }
        }
    }

    fun updatePlaybackSummary(summary: String) {
        runtime.update {
            it.copy(currentPlaybackSummary = summary)
        }
    }

    fun preparePlayback(request: ReaderTtsStartRequest, settings: ReaderTtsSettings) {
        runtime.update {
            it.copy(
                playbackState = ReaderTtsSessionState.STARTING,
                currentBookId = request.bookId,
                currentBookTitle = request.bookTitle,
                currentPlaybackSummary = request.chapterTitleOrSummary,
                activeStateLabel = request.activeStateLabel,
                selectedVoiceName = settings.voiceName,
                speechRate = settings.speechRate,
                pitch = settings.pitch,
                timerPreset = settings.timerPreset,
                localErrorMessage = null,
                localStatusMessage = if (it.notificationControlsAvailable) null else NOTIFICATION_PERMISSION_DEGRADED_MESSAGE,
                isVoicePreviewing = false,
            )
        }
    }

    fun updateListeningProgress(progress: ListeningProgress) {
        runtime.update { it.copy(listeningProgress = progress) }
    }

    fun setVoicePreviewing(previewing: Boolean, error: String? = null) {
        runtime.update { it.copy(isVoicePreviewing = previewing, localErrorMessage = error) }
    }

    fun onServiceDisconnected() {
        runtime.update {
            if (!it.isOngoingSession()) it.copy(isVoicePreviewing = false)
            else it.copy(
                playbackState = ReaderTtsSessionState.PAUSED_BY_USER,
                isVoicePreviewing = false,
                localStatusMessage = "朗读服务已中断，点继续可从已保存位置恢复",
                playbackSnapshot = it.playbackSnapshot.copy(activePauseReason = ReaderTtsPauseReason.USER),
            )
        }
    }

    private fun dispatchCommand(message: String, command: () -> Unit): Boolean {
        return try {
            command()
            true
        } catch (_: Exception) {
            runtime.update { it.copy(localErrorMessage = message) }
            false
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
