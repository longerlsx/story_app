package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState
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
        val isCurrentBook = runtimeState.currentBookId == currentBookId
        val failed = isCurrentBook && runtimeState.playbackState == ReaderTtsSessionState.FAILED
        val remainingTimeLabel = if (currentBookSession.isOngoing) {
            ReaderTtsTimeLabelFormatter.formatPositiveRemainingMillisOrNull(runtimeState.remainingTimerMillis)
        } else {
            null
        }
        val toggleLabel = when {
            runtimeState.isVoicePreviewing -> "停止试听"
            currentBookSession.isPaused && !remainingTimeLabel.isNullOrBlank() -> "继续朗读 · $remainingTimeLabel"
            currentBookSession.isPaused -> "继续朗读"
            currentBookSession.isSpeaking && !remainingTimeLabel.isNullOrBlank() -> "暂停朗读 · $remainingTimeLabel"
            currentBookSession.isSpeaking -> "暂停朗读"
            failed -> "重试朗读"
            else -> "朗读"
        }
        val statusText = when {
            runtimeState.isVoicePreviewing -> "正在试听当前音色…"
            failed -> "朗读失败：${runtimeState.localErrorMessage ?: "请重试"}"
            isCurrentBook && runtimeState.playbackState == ReaderTtsSessionState.STOPPED_BY_TIMER -> "定时结束，已停止朗读"
            isCurrentBook && runtimeState.playbackState == ReaderTtsSessionState.STOPPED_AT_BOOK_END -> "已读到全书末尾"
            isCurrentBook && runtimeState.playbackState == ReaderTtsSessionState.STARTING -> "正在准备离线声音…"
            currentBookSession.isPaused -> "当前状态：已暂停"
            currentBookSession.isSpeaking -> "当前状态：朗读中"
            else -> "当前状态：未朗读"
        }
        return ReaderTtsReaderUiState(
            toggleState = ReaderTtsToggleUiState(
                actionLabel = toggleLabel,
                showImmersiveAction = currentBookSession.isOngoing || runtimeState.isVoicePreviewing,
                immersiveActionLabel = toggleLabel,
            ),
            statusText = statusText,
            remainingTimeLabel = remainingTimeLabel,
        )
    }
}
