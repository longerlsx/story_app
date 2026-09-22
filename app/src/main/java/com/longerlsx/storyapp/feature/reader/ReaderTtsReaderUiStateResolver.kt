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
            currentBookSession.isPaused -> "继续朗读"
            currentBookSession.isSpeaking -> "暂停朗读"
            failed -> "重试朗读"
            else -> "朗读"
        }
        val statusText = when {
            runtimeState.isVoicePreviewing -> "正在试听当前音色…"
            failed -> "朗读失败"
            isCurrentBook && runtimeState.playbackState == ReaderTtsSessionState.STOPPED_BY_TIMER -> "定时结束"
            isCurrentBook && runtimeState.playbackState == ReaderTtsSessionState.STOPPED_AT_BOOK_END -> "已读完"
            isCurrentBook && runtimeState.playbackState == ReaderTtsSessionState.STARTING -> "正在准备离线声音…"
            currentBookSession.isPaused -> "已暂停"
            currentBookSession.isSpeaking -> "正在朗读"
            isCurrentBook && runtimeState.playbackState in setOf(
                ReaderTtsSessionState.STOPPED_BY_USER, ReaderTtsSessionState.STOPPED_BY_NAVIGATION,
            ) -> "已停止"
            else -> "未开始"
        }
        return ReaderTtsReaderUiState(
            toggleState = ReaderTtsToggleUiState(
                actionLabel = if (currentBookSession.isOngoing || failed) "听书" else "朗读",
                showImmersiveAction = currentBookSession.isOngoing || runtimeState.isVoicePreviewing || failed,
                immersiveActionLabel = toggleLabel,
                statusText = statusText,
                remainingTimeLabel = remainingTimeLabel,
                speechRate = runtimeState.speechRate,
            ),
            statusText = statusText,
            remainingTimeLabel = remainingTimeLabel,
        )
    }
}
