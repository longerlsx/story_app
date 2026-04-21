package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBrightnessResolverTest {

    @Test
    fun resolvesBrightnessForCurrentAppearanceMode() {
        val settings = ReaderSettings(
            appearanceMode = ReaderAppearanceMode.NIGHT,
            dayBrightness = 0.8f,
            nightBrightness = 0.3f,
        )

        assertEquals(0.3f, ReaderBrightnessResolver.resolveActiveBrightness(settings))
    }

    @Test
    fun updatesOnlyBrightnessForCurrentAppearanceMode() {
        val settings = ReaderSettings(
            appearanceMode = ReaderAppearanceMode.DAY,
            dayBrightness = 0.8f,
            nightBrightness = 0.3f,
        )

        val updated = ReaderBrightnessResolver.withActiveBrightness(settings, 0.9f)

        assertEquals(0.9f, updated.dayBrightness)
        assertEquals(0.3f, updated.nightBrightness)
    }
}
