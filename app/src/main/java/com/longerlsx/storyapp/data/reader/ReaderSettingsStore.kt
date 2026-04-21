package com.longerlsx.storyapp.data.reader

import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.feature.reader.ReaderThemePreset
import java.io.File
import java.util.Properties

class ReaderSettingsStore(
    private val rootDir: File,
) {
    private val settingsFile = File(rootDir, "reader-settings.properties")

    fun load(): ReaderSettings {
        val properties = loadProperties()
        val defaults = ReaderSettings()
        val legacyThemePreset = properties.getProperty(KEY_THEME_PRESET_LEGACY)
            ?.let(ReaderThemePreset::fromStoredValue)

        return ReaderSettings(
            appearanceMode = properties.getProperty(KEY_APPEARANCE_MODE)
                ?.let(ReaderAppearanceMode::valueOf)
                ?: when (legacyThemePreset?.appearanceMode) {
                    ReaderAppearanceMode.NIGHT -> ReaderAppearanceMode.NIGHT
                    else -> defaults.appearanceMode
                },
            readingMode = properties.getProperty(KEY_READING_MODE)
                ?.let(ReadingMode::valueOf)
                ?: defaults.readingMode,
            fontSizeSp = properties.getProperty(KEY_FONT_SIZE_SP)?.toIntOrNull() ?: defaults.fontSizeSp,
            lineHeightMultiplier = properties.getProperty(KEY_LINE_HEIGHT_MULTIPLIER)?.toFloatOrNull()
                ?: defaults.lineHeightMultiplier,
            paragraphSpacingEm = properties.getProperty(KEY_PARAGRAPH_SPACING_EM)?.toFloatOrNull()
                ?: defaults.paragraphSpacingEm,
            dayBrightness = properties.getProperty(KEY_DAY_BRIGHTNESS)?.toFloatOrNull()
                ?: properties.getProperty(KEY_BRIGHTNESS)?.toFloatOrNull()
                ?: defaults.dayBrightness,
            nightBrightness = properties.getProperty(KEY_NIGHT_BRIGHTNESS)?.toFloatOrNull()
                ?: properties.getProperty(KEY_BRIGHTNESS)?.toFloatOrNull()
                ?: defaults.nightBrightness,
            dayThemePreset = properties.getProperty(KEY_DAY_THEME_PRESET)
                ?.let(ReaderThemePreset::fromStoredValue)
                ?: legacyThemePreset?.takeIf { it.appearanceMode == ReaderAppearanceMode.DAY }
                ?: defaults.dayThemePreset,
            nightThemePreset = properties.getProperty(KEY_NIGHT_THEME_PRESET)
                ?.let(ReaderThemePreset::fromStoredValue)
                ?: legacyThemePreset?.takeIf { it.appearanceMode == ReaderAppearanceMode.NIGHT }
                ?: defaults.nightThemePreset,
        )
    }

    fun save(settings: ReaderSettings) {
        val properties = Properties().apply {
            setProperty(KEY_APPEARANCE_MODE, settings.appearanceMode.name)
            setProperty(KEY_READING_MODE, settings.readingMode.name)
            setProperty(KEY_FONT_SIZE_SP, settings.fontSizeSp.toString())
            setProperty(KEY_LINE_HEIGHT_MULTIPLIER, settings.lineHeightMultiplier.toString())
            setProperty(KEY_PARAGRAPH_SPACING_EM, settings.paragraphSpacingEm.toString())
            setProperty(KEY_DAY_BRIGHTNESS, settings.dayBrightness.toString())
            setProperty(KEY_NIGHT_BRIGHTNESS, settings.nightBrightness.toString())
            setProperty(KEY_DAY_THEME_PRESET, settings.dayThemePreset.name)
            setProperty(KEY_NIGHT_THEME_PRESET, settings.nightThemePreset.name)
        }

        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        settingsFile.outputStream().use { output ->
            properties.store(output, null)
        }
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        if (settingsFile.exists()) {
            settingsFile.inputStream().use(properties::load)
        }
        return properties
    }

    private companion object {
        const val KEY_APPEARANCE_MODE = "appearanceMode"
        const val KEY_READING_MODE = "readingMode"
        const val KEY_FONT_SIZE_SP = "fontSizeSp"
        const val KEY_LINE_HEIGHT_MULTIPLIER = "lineHeightMultiplier"
        const val KEY_PARAGRAPH_SPACING_EM = "paragraphSpacingEm"
        const val KEY_DAY_BRIGHTNESS = "dayBrightness"
        const val KEY_NIGHT_BRIGHTNESS = "nightBrightness"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_DAY_THEME_PRESET = "dayThemePreset"
        const val KEY_NIGHT_THEME_PRESET = "nightThemePreset"
        const val KEY_THEME_PRESET_LEGACY = "themePreset"
    }
}
