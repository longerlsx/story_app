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
    fun oldVoicePreferencesUseBundledReferenceVoiceWithoutRewritingOrLosingOtherSettings() {
        val root = Files.createTempDirectory("story-zipvoice-migration").toFile()
        try {
            for (voice in listOf("kokoro:59", "kokoro:58", "kokoro:3", "kokoro:4", "zh-CN-system-voice")) {
                val original = "ttsVoiceName=$voice\nttsSpeechRate=2.0\nttsPitch=0.9\nttsTimerPreset=COUNTDOWN_60\nfontSizeSp=24\nreadingMode=SCROLL\n"
                val file = root.resolve("reader-settings.properties").apply { writeText(original) }
                val loaded = ReaderSettingsStore(root).load()
                assertEquals("zipvoice:leijun", loaded.ttsSettings.voiceName)
                assertEquals(2f, loaded.ttsSettings.speechRate)
                assertEquals(0.9f, loaded.ttsSettings.pitch)
                assertEquals(ReaderTtsTimerPreset.Countdown(60), loaded.ttsSettings.timerPreset)
                assertEquals(24, loaded.fontSizeSp)
                assertEquals(ReadingMode.SCROLL, loaded.readingMode)
                assertEquals("Reading preferences must not rewrite existing data", original, file.readText())
            }
        } finally {
            root.deleteRecursively()
        }
    }

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
                    voiceName = "zipvoice:leijun",
                    speechRate = 1.45f,
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
    fun legacyOnePointFifteenLoadsAsNormalSpeedWithoutRewritingOtherPreferences() {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-legacy-rate-test").toFile()
        try {
            val file = tempDir.resolve("reader-settings.properties")
            val original = """
                ttsSpeechRate=1.15
                ttsVoiceName=kokoro:58
                ttsPitch=0.95
                ttsTimerPreset=COUNTDOWN_30
                fontSizeSp=24
            """.trimIndent()
            file.writeText(original)

            val settings = ReaderSettingsStore(tempDir).load()

            assertEquals(1f, settings.ttsSettings.speechRate)
            assertEquals("zipvoice:leijun", settings.ttsSettings.voiceName)
            assertEquals(0.95f, settings.ttsSettings.pitch)
            assertEquals(ReaderTtsTimerPreset.Countdown(30), settings.ttsSettings.timerPreset)
            assertEquals(24, settings.fontSizeSp)
            assertEquals(original, file.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun explicitDoubleSpeedAndOtherExistingRatesSurviveStorageReload() = runTest {
        val tempDir = Files.createTempDirectory("story-app-reader-settings-rate-roundtrip-test").toFile()
        try {
            assertEquals(1f, ReaderSettingsStore(tempDir).load().ttsSettings.speechRate)
            for (rate in listOf(0.85f, 1f, 1.3f, 1.45f, 2f, 1.2f)) {
                ReaderSettingsStore(tempDir, writeScope = this).save(
                    ReaderSettings(ttsSettings = ReaderTtsSettings(speechRate = rate)),
                )
                advanceUntilIdle()
                assertEquals(rate, ReaderSettingsStore(tempDir).load().ttsSettings.speechRate)
            }
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
