package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderReadingSettingsAdjustmentPolicyTest {

    @Test
    fun fontSizeStepsByTwoAndStopsAtBounds() {
        assertEquals(
            14,
            ReaderReadingSettingsAdjustmentPolicy.decreaseFontSize(
                ReaderSettings(fontSizeSp = 15),
            ).fontSizeSp,
        )
        assertEquals(
            20,
            ReaderReadingSettingsAdjustmentPolicy.increaseFontSize(
                ReaderSettings(fontSizeSp = 18),
            ).fontSizeSp,
        )
        assertEquals(
            32,
            ReaderReadingSettingsAdjustmentPolicy.increaseFontSize(
                ReaderSettings(fontSizeSp = 31),
            ).fontSizeSp,
        )
    }

    @Test
    fun lineHeightStepsByOneTenthAndStopsAtBounds() {
        assertFloatEquals(
            1.2f,
            ReaderReadingSettingsAdjustmentPolicy.decreaseLineHeight(
                ReaderSettings(lineHeightMultiplier = 1.25f),
            ).lineHeightMultiplier,
        )
        assertFloatEquals(
            1.6f,
            ReaderReadingSettingsAdjustmentPolicy.increaseLineHeight(
                ReaderSettings(lineHeightMultiplier = 1.5f),
            ).lineHeightMultiplier,
        )
        assertFloatEquals(
            2.2f,
            ReaderReadingSettingsAdjustmentPolicy.increaseLineHeight(
                ReaderSettings(lineHeightMultiplier = 2.15f),
            ).lineHeightMultiplier,
        )
    }

    @Test
    fun paragraphSpacingStepsByOneTenthAndStopsAtBounds() {
        assertFloatEquals(
            0.4f,
            ReaderReadingSettingsAdjustmentPolicy.decreaseParagraphSpacing(
                ReaderSettings(paragraphSpacingEm = 0.45f),
            ).paragraphSpacingEm,
        )
        assertFloatEquals(
            1.0f,
            ReaderReadingSettingsAdjustmentPolicy.increaseParagraphSpacing(
                ReaderSettings(paragraphSpacingEm = 0.9f),
            ).paragraphSpacingEm,
        )
        assertFloatEquals(
            1.8f,
            ReaderReadingSettingsAdjustmentPolicy.increaseParagraphSpacing(
                ReaderSettings(paragraphSpacingEm = 1.75f),
            ).paragraphSpacingEm,
        )
    }

    private fun assertFloatEquals(
        expected: Float,
        actual: Float,
    ) {
        assertEquals(expected, actual, 0.0001f)
    }
}
