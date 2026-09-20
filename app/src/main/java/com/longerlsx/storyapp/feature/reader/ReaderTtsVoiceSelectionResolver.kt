package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption

data class ReaderTtsVoiceSelectionUiState(
    val selectedVoiceName: String?,
    val systemDefaultVoiceStatus: String?,
)

object ReaderTtsVoiceSelectionResolver {
    fun resolve(
        persistedVoiceName: String?,
        availableVoices: List<ReaderTtsVoiceOption>,
        availableVoicesLoaded: Boolean,
    ): ReaderTtsVoiceSelectionUiState {
        val persistedOption = persistedVoiceName
            ?.takeIf { voiceName ->
                availableVoices.any { it.name == voiceName }
            }
        val fallback = availableVoices.firstOrNull()?.takeIf { availableVoicesLoaded }
        val selectedVoiceName = persistedOption ?: fallback?.name
        val systemDefaultVoiceStatus = if (
            availableVoicesLoaded &&
            persistedVoiceName != null &&
            persistedOption == null && fallback != null
        ) {
            "原音色不可用，已使用${fallback.displayName}"
        } else {
            null
        }

        return ReaderTtsVoiceSelectionUiState(
            selectedVoiceName = selectedVoiceName,
            systemDefaultVoiceStatus = systemDefaultVoiceStatus,
        )
    }
}
