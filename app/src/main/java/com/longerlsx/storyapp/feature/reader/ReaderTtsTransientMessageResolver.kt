package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState

object ReaderTtsTransientMessageResolver {
    fun resolve(
        currentBookId: String,
        runtimeState: ReaderTtsRuntimeState,
        localRestartFeedbackMessage: String?,
    ): String? {
        val isCurrentBookTts = runtimeState.currentBookId == currentBookId
        return when {
            isCurrentBookTts && !runtimeState.localErrorMessage.isNullOrBlank() -> {
                runtimeState.localErrorMessage
            }

            !localRestartFeedbackMessage.isNullOrBlank() -> {
                localRestartFeedbackMessage
            }

            isCurrentBookTts && !runtimeState.localStatusMessage.isNullOrBlank() -> {
                runtimeState.localStatusMessage
            }

            else -> null
        }
    }
}
