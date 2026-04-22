package com.longerlsx.storyapp.feature.reader.tts

object ReaderTtsNavigationPolicy {
    fun shouldStopForOpenReader(
        playbackState: ReaderTtsSessionState,
        activeBookId: String?,
        nextBookId: String,
    ): Boolean {
        return playbackState.isOngoingSession() &&
            activeBookId != null &&
            activeBookId != nextBookId
    }
}
