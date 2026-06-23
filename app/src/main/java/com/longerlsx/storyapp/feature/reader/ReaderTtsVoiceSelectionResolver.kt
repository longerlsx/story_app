package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption

data class ReaderTtsVoiceSelectionUiState(
    val selectedVoiceName: String?,
    val systemDefaultVoiceStatus: String?,
)

object ReaderTtsVoiceSelectionResolver {
    private const val SystemDefaultVoiceStatus = "当前使用系统默认音色"

    fun resolve(
        persistedVoiceName: String?,
        availableVoices: List<ReaderTtsVoiceOption>,
        availableVoicesLoaded: Boolean,
    ): ReaderTtsVoiceSelectionUiState {
        val selectedVoiceName = persistedVoiceName
            ?.takeIf { voiceName ->
                availableVoices.any { it.name == voiceName }
            }
        val systemDefaultVoiceStatus = if (
            availableVoicesLoaded &&
            persistedVoiceName != null &&
            selectedVoiceName == null
        ) {
            SystemDefaultVoiceStatus
        } else {
            null
        }

        return ReaderTtsVoiceSelectionUiState(
            selectedVoiceName = selectedVoiceName,
            systemDefaultVoiceStatus = systemDefaultVoiceStatus,
        )
    }
}
