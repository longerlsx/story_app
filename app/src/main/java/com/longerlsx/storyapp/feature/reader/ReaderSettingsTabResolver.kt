package com.longerlsx.storyapp.feature.reader

object ReaderSettingsTabResolver {
    fun resolveOnOpen(
        currentTab: ReaderSettingsTab?,
        isCurrentBookTtsOngoing: Boolean,
    ): ReaderSettingsTab {
        return currentTab ?: if (isCurrentBookTtsOngoing) {
            ReaderSettingsTab.TTS
        } else {
            ReaderSettingsTab.READING
        }
    }
}
