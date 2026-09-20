package com.longerlsx.storyapp.feature.reader.tts

import android.media.AudioManager

class ReaderTtsAudioFocusManager(
    private val onPauseForFocusLoss: (focusChange: Int) -> Unit,
    private val onResumeAfterFocusGain: () -> Unit,
    private val canAutoResume: () -> Boolean = { true },
) {
    private var shouldAutoResume = false

    fun onFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                shouldAutoResume = false
                onPauseForFocusLoss(focusChange)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                shouldAutoResume = shouldAutoResume || canAutoResume()
                onPauseForFocusLoss(focusChange)
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (shouldAutoResume) {
                    shouldAutoResume = false
                    onResumeAfterFocusGain()
                }
            }
        }
    }

    fun clearAutoResume() {
        shouldAutoResume = false
    }
}
