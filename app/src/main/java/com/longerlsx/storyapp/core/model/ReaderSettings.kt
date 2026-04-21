package com.longerlsx.storyapp.core.model

import com.longerlsx.storyapp.feature.reader.ReaderThemePreset

data class ReaderSettings(
    val appearanceMode: ReaderAppearanceMode = ReaderAppearanceMode.DAY,
    val readingMode: ReadingMode = ReadingMode.PAGE,
    val fontSizeSp: Int = 18,
    val lineHeightMultiplier: Float = 1.5f,
    val paragraphSpacingEm: Float = 0.9f,
    val dayBrightness: Float = 0.8f,
    val nightBrightness: Float = 0.3f,
    val dayThemePreset: ReaderThemePreset = ReaderThemePreset.PAPER,
    val nightThemePreset: ReaderThemePreset = ReaderThemePreset.AMOLED,
)
