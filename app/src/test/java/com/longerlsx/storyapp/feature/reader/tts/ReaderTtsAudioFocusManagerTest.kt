package com.longerlsx.storyapp.feature.reader.tts

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsAudioFocusManagerTest {

    @Test
    fun pausesOnFocusLossAndResumesOnGainWhenLossTriggeredPause() {
        val events = mutableListOf<String>()
        val manager = ReaderTtsAudioFocusManager(
            onPauseForFocusLoss = { events += "pause" },
            onResumeAfterFocusGain = { events += "resume" },
        )

        manager.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        manager.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(listOf("pause", "resume"), events)
    }

    @Test
    fun doesNotResumeWhenGainArrivesWithoutFocusTriggeredPause() {
        val events = mutableListOf<String>()
        val manager = ReaderTtsAudioFocusManager(
            onPauseForFocusLoss = { events += "pause" },
            onResumeAfterFocusGain = { events += "resume" },
        )

        manager.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun userInterventionClearsAutoResumeBeforeFocusReturns() {
        val events = mutableListOf<String>()
        val manager = ReaderTtsAudioFocusManager(
            onPauseForFocusLoss = { events += "pause" },
            onResumeAfterFocusGain = { events += "resume" },
        )

        manager.onFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        manager.clearAutoResume()
        manager.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(listOf("pause"), events)
    }
}
