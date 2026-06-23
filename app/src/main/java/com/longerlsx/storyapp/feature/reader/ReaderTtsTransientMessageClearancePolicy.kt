package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState

data class ReaderTtsTransientMessageClearance(
    val clearLocalErrorMessage: Boolean,
    val clearLocalStatusMessage: Boolean,
)

object ReaderTtsTransientMessageClearancePolicy {
    fun resolve(
        currentBookId: String,
        runtimeState: ReaderTtsRuntimeState,
        displayedMessage: String?,
    ): ReaderTtsTransientMessageClearance {
        if (displayedMessage.isNullOrBlank() || runtimeState.currentBookId != currentBookId) {
            return ReaderTtsTransientMessageClearance(
                clearLocalErrorMessage = false,
                clearLocalStatusMessage = false,
            )
        }

        return ReaderTtsTransientMessageClearance(
            clearLocalErrorMessage = runtimeState.localErrorMessage == displayedMessage,
            clearLocalStatusMessage = runtimeState.localStatusMessage == displayedMessage,
        )
    }
}
