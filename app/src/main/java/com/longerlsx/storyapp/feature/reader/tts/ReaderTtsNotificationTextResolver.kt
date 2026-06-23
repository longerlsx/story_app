package com.longerlsx.storyapp.feature.reader.tts

object ReaderTtsNotificationTextResolver {
    fun resolveContentText(runtimeState: ReaderTtsRuntimeState): String {
        val summaryLine = runtimeState.currentPlaybackSummary.orEmpty()
        val actionIsResume = runtimeState.playbackState.isPausedSession()
        val remainingTimeLabel = ReaderTtsTimeLabelFormatter
            .formatPositiveRemainingMillisOrNull(runtimeState.remainingTimerMillis)
        val stateLine = when {
            actionIsResume && !remainingTimeLabel.isNullOrBlank() -> "已暂停 · $remainingTimeLabel"
            actionIsResume -> "已暂停"
            !remainingTimeLabel.isNullOrBlank() -> remainingTimeLabel
            else -> runtimeState.activeStateLabel
        }
        return listOfNotNull(
            summaryLine.takeIf(String::isNotBlank),
            stateLine.takeIf(String::isNotBlank),
        ).joinToString(" · ")
            .ifBlank { runtimeState.activeStateLabel }
    }
}
