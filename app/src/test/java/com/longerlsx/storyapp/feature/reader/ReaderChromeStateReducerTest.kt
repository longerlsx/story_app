package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderChromeStateReducerTest {

    @Test
    fun chromeModesDoNotExposeDedicatedTtsExpandedState() {
        assertFalse(ReaderChromeMode.entries.map { it.name }.contains("TTS_SETTINGS_EXPANDED"))
    }

    @Test
    fun centerTapTogglesBetweenReadingAndChrome() {
        assertEquals(
            ReaderChromeMode.CHROME_VISIBLE,
            ReaderChromeStateReducer.onCenterTap(ReaderChromeMode.READING_ONLY),
        )
        assertEquals(
            ReaderChromeMode.READING_ONLY,
            ReaderChromeStateReducer.onCenterTap(ReaderChromeMode.CHROME_VISIBLE),
        )
    }

    @Test
    fun settingsAndDirectoryAreMutuallyExclusive() {
        assertEquals(
            ReaderChromeMode.SETTINGS_EXPANDED,
            ReaderChromeStateReducer.onOpenSettings(ReaderChromeMode.CHROME_VISIBLE),
        )
        assertEquals(
            ReaderChromeMode.DIRECTORY_OPEN,
            ReaderChromeStateReducer.onOpenDirectory(ReaderChromeMode.SETTINGS_EXPANDED),
        )
    }

    @Test
    fun openingSettingsOrDirectoryFromExpandedModesKeepsOneExpandedSurface() {
        assertEquals(
            ReaderChromeMode.SETTINGS_EXPANDED,
            ReaderChromeStateReducer.onOpenSettings(ReaderChromeMode.DIRECTORY_OPEN),
        )
        assertEquals(
            ReaderChromeMode.DIRECTORY_OPEN,
            ReaderChromeStateReducer.onOpenDirectory(ReaderChromeMode.SETTINGS_EXPANDED),
        )
    }

    @Test
    fun directorySelectionReturnsToReadingOnly() {
        assertEquals(
            ReaderChromeMode.READING_ONLY,
            ReaderChromeStateReducer.onDirectoryChapterSelected(),
        )
    }

    @Test
    fun togglingSettingsTwiceReturnsToChromeVisible() {
        assertEquals(
            ReaderChromeMode.CHROME_VISIBLE,
            ReaderChromeStateReducer.onOpenSettings(ReaderChromeMode.SETTINGS_EXPANDED),
        )
    }

    @Test
    fun chapterStepKeepsChromeVisible() {
        assertEquals(
            ReaderChromeMode.CHROME_VISIBLE,
            ReaderChromeStateReducer.onChapterStep(ReaderChromeMode.CHROME_VISIBLE),
        )
    }
}
