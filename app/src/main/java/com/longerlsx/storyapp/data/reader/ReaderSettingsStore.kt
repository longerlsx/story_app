package com.longerlsx.storyapp.data.reader

import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.data.book.writeFileAtomically
import com.longerlsx.storyapp.feature.reader.ReaderThemePreset
import java.io.File
import java.io.IOException
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderSettingsStore(
    private val rootDir: File,
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val settingsFile = File(rootDir, "reader-settings.properties")
    private val lock = Any()
    private var currentSettings: ReaderSettings? = null
    private var pendingWrite: Pair<ReaderSettings, CompletableDeferred<Unit>>? = null
    private var latestWrite: CompletableDeferred<Unit>? = null
    private var writerRunning = false
    private val mutableWriteError = MutableStateFlow<String?>(null)
    val writeError = mutableWriteError.asStateFlow()

    // The application warms this cache during background library initialization.
    // A setting just changed in the UI is readable before its disk write completes.
    fun load(): ReaderSettings = synchronized(lock) {
        currentSettings ?: readSettings().also { currentSettings = it }
    }

    private fun readSettings(): ReaderSettings {
        val properties = loadProperties()
        val defaults = ReaderSettings()
        val legacyThemePreset = properties.getProperty(KEY_THEME_PRESET_LEGACY)
            ?.let(ReaderThemePreset::fromStoredValue)

        return ReaderSettings(
            appearanceMode = ReaderAppearanceMode.entries.firstOrNull {
                it.name == properties.getProperty(KEY_APPEARANCE_MODE)
            }
                ?: when (legacyThemePreset?.appearanceMode) {
                    ReaderAppearanceMode.NIGHT -> ReaderAppearanceMode.NIGHT
                    else -> defaults.appearanceMode
                },
            readingMode = ReadingMode.entries.firstOrNull {
                it.name == properties.getProperty(KEY_READING_MODE)
            }
                ?: defaults.readingMode,
            fontSizeSp = properties.getProperty(KEY_FONT_SIZE_SP)?.toIntOrNull()
                ?.takeIf { it in 14..32 } ?: defaults.fontSizeSp,
            lineHeightMultiplier = properties.validFloat(KEY_LINE_HEIGHT_MULTIPLIER, 1.2f..2.2f)
                ?: defaults.lineHeightMultiplier,
            paragraphSpacingEm = properties.validFloat(KEY_PARAGRAPH_SPACING_EM, 0.4f..1.8f)
                ?: defaults.paragraphSpacingEm,
            dayBrightness = properties.validFloat(KEY_DAY_BRIGHTNESS, 0.1f..1f)
                ?: properties.validFloat(KEY_BRIGHTNESS, 0.1f..1f)
                ?: defaults.dayBrightness,
            nightBrightness = properties.validFloat(KEY_NIGHT_BRIGHTNESS, 0.1f..1f)
                ?: properties.validFloat(KEY_BRIGHTNESS, 0.1f..1f)
                ?: defaults.nightBrightness,
            dayThemePreset = properties.getProperty(KEY_DAY_THEME_PRESET)
                ?.let(ReaderThemePreset::fromStoredValue)
                ?.takeIf { it.appearanceMode == ReaderAppearanceMode.DAY }
                ?: legacyThemePreset?.takeIf { it.appearanceMode == ReaderAppearanceMode.DAY }
                ?: defaults.dayThemePreset,
            nightThemePreset = properties.getProperty(KEY_NIGHT_THEME_PRESET)
                ?.let(ReaderThemePreset::fromStoredValue)
                ?.takeIf { it.appearanceMode == ReaderAppearanceMode.NIGHT }
                ?: legacyThemePreset?.takeIf { it.appearanceMode == ReaderAppearanceMode.NIGHT }
                ?: defaults.nightThemePreset,
            ttsSettings = ReaderTtsSettings(
                voiceName = properties.getProperty(KEY_TTS_VOICE_NAME),
                speechRate = properties.getProperty(KEY_TTS_SPEECH_RATE)?.toFloatOrNull()
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?: defaults.ttsSettings.speechRate,
                pitch = properties.getProperty(KEY_TTS_PITCH)?.toFloatOrNull()
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?: defaults.ttsSettings.pitch,
                timerPreset = ReaderTtsTimerPreset.fromStoredValue(
                    properties.getProperty(KEY_TTS_TIMER_PRESET),
                ) ?: defaults.ttsSettings.timerPreset,
            ),
        )
    }

    fun save(settings: ReaderSettings) {
        synchronized(lock) {
            currentSettings = settings
            val completion = pendingWrite?.second ?: CompletableDeferred<Unit>()
            pendingWrite = settings to completion
            latestWrite = completion
            if (writerRunning) return
            writerRunning = true
        }
        writeScope.launch {
            while (true) {
                val next = synchronized(lock) {
                    pendingWrite.also {
                        pendingWrite = null
                        if (it == null) writerRunning = false
                    }
                } ?: break
                try {
                    persist(next.first)
                    mutableWriteError.value = null
                } catch (cancelled: CancellationException) {
                    synchronized(lock) { writerRunning = false }
                    throw cancelled
                } catch (_: Exception) {
                    mutableWriteError.value = "阅读设置保存失败，请稍后重新调整设置。"
                } finally {
                    next.second.complete(Unit)
                }
            }
        }
    }

    suspend fun awaitPendingWrites() {
        val pending = synchronized(lock) { latestWrite }
        pending?.await()
    }

    private fun persist(settings: ReaderSettings) {
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
            settings.ttsSettings.voiceName?.let { setProperty(KEY_TTS_VOICE_NAME, it) }
            setProperty(KEY_TTS_SPEECH_RATE, settings.ttsSettings.speechRate.toString())
            setProperty(KEY_TTS_PITCH, settings.ttsSettings.pitch.toString())
            setProperty(KEY_TTS_TIMER_PRESET, settings.ttsSettings.timerPreset.storedValue)
        }

        writeFileAtomically(settingsFile) { output ->
            properties.store(output, null)
        }
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        try {
            if (settingsFile.exists()) {
                settingsFile.inputStream().use(properties::load)
            }
        } catch (_: IOException) {
            return Properties()
        } catch (_: IllegalArgumentException) {
            return Properties()
        }
        return properties
    }

    private fun Properties.validFloat(key: String, range: ClosedFloatingPointRange<Float>): Float? =
        getProperty(key)?.toFloatOrNull()?.takeIf { it.isFinite() && it in range }

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
        const val KEY_TTS_VOICE_NAME = "ttsVoiceName"
        const val KEY_TTS_SPEECH_RATE = "ttsSpeechRate"
        const val KEY_TTS_PITCH = "ttsPitch"
        const val KEY_TTS_TIMER_PRESET = "ttsTimerPreset"
    }
}
