package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.isPausedSession
import com.longerlsx.storyapp.feature.reader.tts.isSpeakingSession

data class ReaderTtsCurrentBookSession(
    val isSpeaking: Boolean,
    val isPaused: Boolean,
) {
    val isOngoing: Boolean = isSpeaking || isPaused
}

object ReaderTtsCurrentBookSessionResolver {
    fun resolve(
        currentBookId: String,
        runtimeState: ReaderTtsRuntimeState,
    ): ReaderTtsCurrentBookSession {
        val isCurrentBook = runtimeState.currentBookId == currentBookId
        return ReaderTtsCurrentBookSession(
            isSpeaking = isCurrentBook && runtimeState.playbackState.isSpeakingSession(),
            isPaused = isCurrentBook && runtimeState.playbackState.isPausedSession(),
        )
    }
}
