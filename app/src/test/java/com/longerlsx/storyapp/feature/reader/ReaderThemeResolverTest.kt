package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderThemeResolverTest {

    @Test
    fun resolvesDayThemeWhenAppearanceModeIsDay() {
        val settings = ReaderSettings(
            appearanceMode = ReaderAppearanceMode.DAY,
            dayThemePreset = ReaderThemePreset.SEPIA,
            nightThemePreset = ReaderThemePreset.AMOLED,
        )

        assertEquals(ReaderThemePreset.SEPIA, ReaderThemeResolver.resolveActivePreset(settings))
    }

    @Test
    fun resolvesNightThemeWhenAppearanceModeIsNight() {
        val settings = ReaderSettings(
            appearanceMode = ReaderAppearanceMode.NIGHT,
            dayThemePreset = ReaderThemePreset.PAPER,
            nightThemePreset = ReaderThemePreset.AMOLED,
        )

        assertEquals(ReaderThemePreset.AMOLED, ReaderThemeResolver.resolveActivePreset(settings))
    }
}
