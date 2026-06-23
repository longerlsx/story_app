package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsTimeLabelFormatter
import com.longerlsx.storyapp.feature.reader.tts.isPausedSession
import com.longerlsx.storyapp.feature.reader.tts.isSpeakingSession

data class ReaderTtsReaderUiState(
    val toggleState: ReaderTtsToggleUiState,
    val statusText: String,
    val remainingTimeLabel: String?,
)

object ReaderTtsReaderUiStateResolver {
    fun resolve(
        currentBookId: String,
        runtimeState: ReaderTtsRuntimeState,
    ): ReaderTtsReaderUiState {
        val isCurrentBook = runtimeState.currentBookId == currentBookId
        val isPlaying = isCurrentBook && runtimeState.playbackState.isSpeakingSession()
        val isPaused = isCurrentBook && runtimeState.playbackState.isPausedSession()
        val isOngoing = isPlaying || isPaused
        val remainingTimeLabel = if (isOngoing) {
            ReaderTtsTimeLabelFormatter.formatPositiveRemainingMillisOrNull(runtimeState.remainingTimerMillis)
        } else {
            null
        }
        val toggleLabel = when {
            isPaused && !remainingTimeLabel.isNullOrBlank() -> "继续朗读 · $remainingTimeLabel"
            isPaused -> "继续朗读"
            isPlaying && !remainingTimeLabel.isNullOrBlank() -> "停止朗读 · $remainingTimeLabel"
            isPlaying -> "停止朗读"
            else -> "朗读"
        }
        val statusText = when {
            isPaused -> "当前状态：已暂停"
            isPlaying -> "当前状态：朗读中"
            else -> "当前状态：未朗读"
        }
        return ReaderTtsReaderUiState(
            toggleState = ReaderTtsToggleUiState(
                actionLabel = toggleLabel,
                showImmersiveAction = isOngoing,
                immersiveActionLabel = toggleLabel,
            ),
            statusText = statusText,
            remainingTimeLabel = remainingTimeLabel,
        )
    }
}
