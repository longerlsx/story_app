package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReaderSettings

object ReaderReadingSettingsAdjustmentPolicy {
    private const val FONT_SIZE_STEP_SP = 2
    private const val FONT_SIZE_MIN_SP = 14
    private const val FONT_SIZE_MAX_SP = 32

    private const val LINE_HEIGHT_STEP = 0.1f
    private const val LINE_HEIGHT_MIN = 1.2f
    private const val LINE_HEIGHT_MAX = 2.2f

    private const val PARAGRAPH_SPACING_STEP = 0.1f
    private const val PARAGRAPH_SPACING_MIN = 0.4f
    private const val PARAGRAPH_SPACING_MAX = 1.8f

    fun decreaseFontSize(settings: ReaderSettings): ReaderSettings {
        return settings.copy(
            fontSizeSp = (settings.fontSizeSp - FONT_SIZE_STEP_SP).coerceAtLeast(FONT_SIZE_MIN_SP),
        )
    }

    fun increaseFontSize(settings: ReaderSettings): ReaderSettings {
        return settings.copy(
            fontSizeSp = (settings.fontSizeSp + FONT_SIZE_STEP_SP).coerceAtMost(FONT_SIZE_MAX_SP),
        )
    }

    fun decreaseLineHeight(settings: ReaderSettings): ReaderSettings {
        return settings.copy(
            lineHeightMultiplier = (settings.lineHeightMultiplier - LINE_HEIGHT_STEP).coerceAtLeast(LINE_HEIGHT_MIN),
        )
    }

    fun increaseLineHeight(settings: ReaderSettings): ReaderSettings {
        return settings.copy(
            lineHeightMultiplier = (settings.lineHeightMultiplier + LINE_HEIGHT_STEP).coerceAtMost(LINE_HEIGHT_MAX),
        )
    }

    fun decreaseParagraphSpacing(settings: ReaderSettings): ReaderSettings {
        return settings.copy(
            paragraphSpacingEm = (settings.paragraphSpacingEm - PARAGRAPH_SPACING_STEP)
                .coerceAtLeast(PARAGRAPH_SPACING_MIN),
        )
    }

    fun increaseParagraphSpacing(settings: ReaderSettings): ReaderSettings {
        return settings.copy(
            paragraphSpacingEm = (settings.paragraphSpacingEm + PARAGRAPH_SPACING_STEP)
                .coerceAtMost(PARAGRAPH_SPACING_MAX),
        )
    }
}
