package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReaderAppearanceMode

enum class ReaderThemePreset {
    PAPER,
    SEPIA,
    MINT,
    SKY,
    CHARCOAL,
    SLATE,
    AMOLED;

    val appearanceMode: ReaderAppearanceMode
        get() = when (this) {
            PAPER,
            SEPIA,
            MINT,
            SKY,
            -> ReaderAppearanceMode.DAY

            CHARCOAL,
            SLATE,
            AMOLED,
            -> ReaderAppearanceMode.NIGHT
        }

    val label: String
        get() = when (this) {
            PAPER -> "纸白"
            SEPIA -> "暖黄"
            MINT -> "浅绿"
            SKY -> "淡蓝"
            CHARCOAL -> "深灰"
            SLATE -> "墨蓝"
            AMOLED -> "纯黑"
        }

    companion object {
        fun fromStoredValue(value: String): ReaderThemePreset? {
            return when (value) {
                "NIGHT" -> AMOLED
                else -> entries.firstOrNull { it.name == value }
            }
        }
    }
}
