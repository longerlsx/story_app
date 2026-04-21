package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings

object ReaderThemeResolver {
    fun resolveActivePreset(settings: ReaderSettings): ReaderThemePreset {
        return when (settings.appearanceMode) {
            ReaderAppearanceMode.DAY -> settings.dayThemePreset
            ReaderAppearanceMode.NIGHT -> settings.nightThemePreset
        }
    }

    fun resolveActivePalette(settings: ReaderSettings): ReaderThemePalette {
        return resolveActivePreset(settings).palette()
    }

    fun presetsFor(mode: ReaderAppearanceMode): List<ReaderThemePreset> {
        return ReaderThemePreset.entries.filter { it.appearanceMode == mode }
    }
}
