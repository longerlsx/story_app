package com.longerlsx.storyapp.data.reader

import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.feature.reader.ReaderThemePreset
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSettingsStoreTest {

    @Test
    fun queuedSettingsAreImmediatelyReadableAndOnlyTheLatestSnapshotIsPersisted() = runTest {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-queued-test").toFile()
        try {
            val store = ReaderSettingsStore(tempDir, writeScope = this)
            store.save(ReaderSettings(fontSizeSp = 20))
            val latest = ReaderSettings(fontSizeSp = 24, nightBrightness = 0.45f)
            store.save(latest)

            assertEquals(latest, store.load())
            assertFalse(tempDir.resolve("reader-settings.properties").exists())
            advanceUntilIdle()
            assertEquals(latest, ReaderSettingsStore(tempDir).load())
            assertEquals(listOf("reader-settings.properties"), tempDir.list()?.toList())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun invalidStoredFieldsFallBackWithoutDiscardingValidSettings() {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-invalid-test").toFile()
        try {
            tempDir.resolve("reader-settings.properties").writeText(
                """
                appearanceMode=UNKNOWN
                readingMode=SCROLL
                fontSizeSp=-4
                lineHeightMultiplier=NaN
                paragraphSpacingEm=Infinity
                dayBrightness=4
                nightBrightness=0.28
                dayThemePreset=AMOLED
                nightThemePreset=PAPER
                ttsSpeechRate=-1
                ttsPitch=NaN
                """.trimIndent(),
            )

            assertEquals(
                ReaderSettings(readingMode = ReadingMode.SCROLL, nightBrightness = 0.28f),
                ReaderSettingsStore(tempDir).load(),
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun malformedPropertiesReturnDefaultsWithoutOverwritingTheOriginalFile() {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-corrupt-test").toFile()
        try {
            val file = tempDir.resolve("reader-settings.properties")
            val corruptText = "fontSizeSp=\\uZZZZ"
            file.writeText(corruptText)

            assertEquals(ReaderSettings(), ReaderSettingsStore(tempDir).load())
            assertEquals(corruptText, file.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun defaultReaderSettingsUsePageMode() {
        assertEquals(ReadingMode.PAGE, ReaderSettings().readingMode)
    }

    @Test
    fun savesAndLoadsReaderSettingsAcrossStoreRecreation() = runTest {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-test").toFile()
        try {
            val settings = ReaderSettings(
                appearanceMode = ReaderAppearanceMode.NIGHT,
                readingMode = ReadingMode.PAGE,
                fontSizeSp = 22,
                lineHeightMultiplier = 1.8f,
                paragraphSpacingEm = 1.2f,
                dayBrightness = 0.82f,
                nightBrightness = 0.28f,
                dayThemePreset = ReaderThemePreset.PAPER,
                nightThemePreset = ReaderThemePreset.AMOLED,
                ttsSettings = ReaderTtsSettings(
                    voiceName = "zh-CN-XiaoxiaoNeural",
                    speechRate = 1.15f,
                    pitch = 0.95f,
                    timerPreset = ReaderTtsTimerPreset.Countdown(15),
                ),
            )

            ReaderSettingsStore(tempDir, writeScope = this).save(settings)
            advanceUntilIdle()

            assertEquals(settings, ReaderSettingsStore(tempDir).load())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun returnsDefaultsWhenNoSettingsFileExists() {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-default-test").toFile()
        try {
            assertEquals(ReaderSettings(), ReaderSettingsStore(tempDir).load())
            assertEquals(ReaderTtsTimerPreset.NoTimer, ReaderSettings().ttsSettings.timerPreset)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun loadsLegacySingleBrightnessIntoBothAppearanceModes() {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-legacy-brightness-test").toFile()
        try {
            tempDir.resolve("reader-settings.properties").writeText(
                """
                brightness=0.66
                """.trimIndent(),
            )

            val settings = ReaderSettingsStore(tempDir).load()

            assertEquals(0.66f, settings.dayBrightness)
            assertEquals(0.66f, settings.nightBrightness)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun loadsExplicitNoTimerPresetFromStoredSettings() {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-explicit-no-timer-test").toFile()
        try {
            tempDir.resolve("reader-settings.properties").writeText(
                """
                ttsTimerPreset=NO_TIMER
                """.trimIndent(),
            )

            val settings = ReaderSettingsStore(tempDir).load()

            assertEquals(ReaderTtsTimerPreset.NoTimer, settings.ttsSettings.timerPreset)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun fallsBackToNoTimerForUnsupportedCountdownPreset() {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-invalid-timer-test").toFile()
        try {
            tempDir.resolve("reader-settings.properties").writeText(
                """
                ttsTimerPreset=COUNTDOWN_7
                """.trimIndent(),
            )

            val settings = ReaderSettingsStore(tempDir).load()

            assertEquals(ReaderTtsTimerPreset.NoTimer, settings.ttsSettings.timerPreset)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
