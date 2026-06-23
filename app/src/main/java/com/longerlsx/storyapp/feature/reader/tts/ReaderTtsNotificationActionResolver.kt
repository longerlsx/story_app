package com.longerlsx.storyapp.feature.reader.tts

enum class ReaderTtsNotificationPrimaryAction {
    PAUSE,
    RESUME,
}

object ReaderTtsNotificationActionResolver {
    fun resolvePrimaryAction(playbackState: ReaderTtsSessionState): ReaderTtsNotificationPrimaryAction {
        return if (playbackState.isPausedSession()) {
            ReaderTtsNotificationPrimaryAction.RESUME
        } else {
            ReaderTtsNotificationPrimaryAction.PAUSE
        }
    }
}
