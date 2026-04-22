package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset

data class ReaderTtsRuntimeState(
    val playbackState: ReaderTtsSessionState = ReaderTtsSessionState.OFF,
    val currentBookId: String? = null,
    val currentBookTitle: String? = null,
    val currentPlaybackSummary: String? = null,
    val activeStateLabel: String = "朗读中",
    val localErrorMessage: String? = null,
    val localStatusMessage: String? = null,
    val playbackSnapshot: ReaderTtsPlaybackSnapshot = ReaderTtsPlaybackSnapshot(),
    val timerPreset: ReaderTtsTimerPreset = ReaderTtsTimerPreset.NoTimer,
    val remainingTimerMillis: Long? = null,
    val selectedVoiceName: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val availableVoices: List<ReaderTtsVoiceOption> = emptyList(),
    val notificationControlsAvailable: Boolean = true,
) {
    fun isOngoingSession(): Boolean {
        return playbackState.isOngoingSession()
    }
}
