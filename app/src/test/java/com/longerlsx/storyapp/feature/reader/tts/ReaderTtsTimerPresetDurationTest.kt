package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTtsTimerPresetDurationTest {

    @Test
    fun noTimerHasNoInitialCountdownBudget() {
        assertNull(ReaderTtsTimerPreset.NoTimer.toInitialTimerMillis())
    }

    @Test
    fun countdownMinutesBecomeInitialCountdownMillis() {
        assertEquals(
            90 * 60_000L,
            ReaderTtsTimerPreset.Countdown(minutes = 90).toInitialTimerMillis(),
        )
    }
}
