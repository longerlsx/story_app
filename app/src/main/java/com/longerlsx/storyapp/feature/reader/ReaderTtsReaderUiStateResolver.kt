package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsTimeLabelFormatter

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
        val currentBookSession = ReaderTtsCurrentBookSessionResolver.resolve(
            currentBookId = currentBookId,
            runtimeState = runtimeState,
        )
        val remainingTimeLabel = if (currentBookSession.isOngoing) {
            ReaderTtsTimeLabelFormatter.formatPositiveRemainingMillisOrNull(runtimeState.remainingTimerMillis)
        } else {
            null
        }
        val toggleLabel = when {
            currentBookSession.isPaused && !remainingTimeLabel.isNullOrBlank() -> "继续朗读 · $remainingTimeLabel"
            currentBookSession.isPaused -> "继续朗读"
            currentBookSession.isSpeaking && !remainingTimeLabel.isNullOrBlank() -> "停止朗读 · $remainingTimeLabel"
            currentBookSession.isSpeaking -> "停止朗读"
            else -> "朗读"
        }
        val statusText = when {
            currentBookSession.isPaused -> "当前状态：已暂停"
            currentBookSession.isSpeaking -> "当前状态：朗读中"
            else -> "当前状态：未朗读"
        }
        return ReaderTtsReaderUiState(
            toggleState = ReaderTtsToggleUiState(
                actionLabel = toggleLabel,
                showImmersiveAction = currentBookSession.isOngoing,
                immersiveActionLabel = toggleLabel,
            ),
            statusText = statusText,
            remainingTimeLabel = remainingTimeLabel,
        )
    }
}
