package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings

object ReaderBrightnessResolver {
    fun resolveActiveBrightness(settings: ReaderSettings): Float {
        return when (settings.appearanceMode) {
            ReaderAppearanceMode.DAY -> settings.dayBrightness
            ReaderAppearanceMode.NIGHT -> settings.nightBrightness
        }
    }

    fun withActiveBrightness(
        settings: ReaderSettings,
        brightness: Float,
    ): ReaderSettings {
        val clampedBrightness = brightness.coerceIn(0.1f, 1f)
        return when (settings.appearanceMode) {
            ReaderAppearanceMode.DAY -> settings.copy(dayBrightness = clampedBrightness)
            ReaderAppearanceMode.NIGHT -> settings.copy(nightBrightness = clampedBrightness)
        }
    }
}
