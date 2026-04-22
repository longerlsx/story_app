package com.longerlsx.storyapp.feature.reader.tts

import android.media.AudioManager

class ReaderTtsAudioFocusManager(
    private val onPauseForFocusLoss: () -> Unit,
    private val onResumeAfterFocusGain: () -> Unit,
) {
    private var shouldAutoResume = false

    fun onFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                shouldAutoResume = true
                onPauseForFocusLoss()
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
