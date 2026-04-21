package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

data class ReaderSystemBarStyle(
    val barColorArgb: Int,
    val useDarkIcons: Boolean,
    val enforceNavigationBarContrast: Boolean,
)

object ReaderSystemBarStyleResolver {
    fun resolve(themePalette: ReaderThemePalette): ReaderSystemBarStyle {
        return ReaderSystemBarStyle(
            barColorArgb = themePalette.background.toArgb(),
            useDarkIcons = themePalette.background.luminance() > 0.5f,
            enforceNavigationBarContrast = false,
        )
    }
}
