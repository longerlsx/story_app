package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption
import kotlin.math.roundToInt

@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    themePalette: ReaderThemePalette,
    activeTab: ReaderSettingsTab,
    availableVoices: List<ReaderTtsVoiceOption>,
    availableVoicesLoaded: Boolean = true,
    selectedVoiceName: String?,
    ttsStatusText: String,
    ttsRemainingTimeLabel: String? = null,
    ttsSystemDefaultVoiceStatus: String? = null,
    onSelectTab: (ReaderSettingsTab) -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onUpdateTtsSettings: (ReaderTtsSettings) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ReaderSettingsTabRow(
            activeTab = activeTab,
            themePalette = themePalette,
            onSelectTab = onSelectTab,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (activeTab) {
                ReaderSettingsTab.READING -> ReaderReadingSettingsBody(
                    settings = settings,
                    themePalette = themePalette,
                    onUpdateSettings = onUpdateSettings,
                )

                ReaderSettingsTab.TTS -> ReaderTtsSettingsSheet(
                    statusText = ttsStatusText,
                    settings = settings.ttsSettings,
                    themePalette = themePalette,
                    availableVoices = availableVoices,
                    availableVoicesLoaded = availableVoicesLoaded,
                    selectedVoiceName = selectedVoiceName,
                    remainingTimeLabel = ttsRemainingTimeLabel,
                    systemDefaultVoiceStatus = ttsSystemDefaultVoiceStatus,
                    onSelectVoice = { voiceName ->
                        onUpdateTtsSettings(settings.ttsSettings.copy(voiceName = voiceName))
                    },
                    onUpdateSpeechRate = { speechRate ->
                        onUpdateTtsSettings(settings.ttsSettings.copy(speechRate = speechRate))
                    },
                    onUpdatePitch = { pitch ->
                        onUpdateTtsSettings(settings.ttsSettings.copy(pitch = pitch))
                    },
                    onUpdateTimerPreset = { timerPreset ->
                        onUpdateTtsSettings(settings.ttsSettings.copy(timerPreset = timerPreset))
                    },
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingsTabRow(
    activeTab: ReaderSettingsTab,
    themePalette: ReaderThemePalette,
    onSelectTab: (ReaderSettingsTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReaderSettingsTab.entries.forEach { tab ->
            ReaderInlinePill(
                label = tab.label,
                themePalette = themePalette,
                modifier = Modifier.weight(1f),
                selected = tab == activeTab,
                contentDescription = "设置分页：${tab.label}，${if (tab == activeTab) "已选中" else "未选中"}",
                onClick = { onSelectTab(tab) },
            )
        }
    }
}

@Composable
private fun ReaderReadingSettingsBody(
    settings: ReaderSettings,
    themePalette: ReaderThemePalette,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    val activeBrightness = ReaderBrightnessResolver.resolveActiveBrightness(settings)
    ReaderSliderRow(
        title = "亮度",
        valueText = "${(activeBrightness * 100).roundToInt()}%",
        value = activeBrightness,
        valueRange = 0.1f..1f,
        themePalette = themePalette,
        onValueChange = {
            onUpdateSettings(
                ReaderBrightnessResolver.withActiveBrightness(settings, it),
            )
        },
    )

    ReaderStepperRow(
        title = "字号",
        valueText = settings.fontSizeSp.toString(),
        themePalette = themePalette,
        onDecrease = {
            onUpdateSettings(
                settings.copy(fontSizeSp = (settings.fontSizeSp - 2).coerceAtLeast(14)),
            )
        },
        onIncrease = {
            onUpdateSettings(
                settings.copy(fontSizeSp = (settings.fontSizeSp + 2).coerceAtMost(32)),
            )
        },
    )

    ReaderStepperRow(
        title = "行距",
        valueText = settings.lineHeightMultiplier.formatOneDecimal(),
        themePalette = themePalette,
        onDecrease = {
            onUpdateSettings(
                settings.copy(
                    lineHeightMultiplier = (settings.lineHeightMultiplier - 0.1f).coerceAtLeast(1.2f),
                ),
            )
        },
        onIncrease = {
            onUpdateSettings(
                settings.copy(
                    lineHeightMultiplier = (settings.lineHeightMultiplier + 0.1f).coerceAtMost(2.2f),
                ),
            )
        },
    )

    ReaderStepperRow(
        title = "段落距",
        valueText = settings.paragraphSpacingEm.formatOneDecimal(),
        themePalette = themePalette,
        onDecrease = {
            onUpdateSettings(
                settings.copy(
                    paragraphSpacingEm = (settings.paragraphSpacingEm - 0.1f).coerceAtLeast(0.4f),
                ),
            )
        },
        onIncrease = {
            onUpdateSettings(
                settings.copy(
                    paragraphSpacingEm = (settings.paragraphSpacingEm + 0.1f).coerceAtMost(1.8f),
                ),
            )
        },
    )

    ReaderModeRow(
        readingMode = settings.readingMode,
        settings = settings,
        themePalette = themePalette,
        onUpdateSettings = onUpdateSettings,
    )

    ReaderThemeRow(
        appearanceMode = settings.appearanceMode,
        selectedPreset = ReaderThemeResolver.resolveActivePreset(settings),
        themePalette = themePalette,
        onSelectPreset = { preset ->
            onUpdateSettings(
                when (settings.appearanceMode) {
                    ReaderAppearanceMode.DAY -> settings.copy(dayThemePreset = preset)
                    ReaderAppearanceMode.NIGHT -> settings.copy(nightThemePreset = preset)
                },
            )
        },
    )
}

@Composable
private fun ReaderSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    themePalette: ReaderThemePalette,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.semantics {
            contentDescription = "$title：$valueText"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = themePalette.content,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = themePalette.content.copy(alpha = 0.7f),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = themePalette.content.copy(alpha = 0.78f),
                activeTrackColor = themePalette.content.copy(alpha = 0.52f),
                inactiveTrackColor = themePalette.content.copy(alpha = 0.16f),
            ),
        )
    }
}

@Composable
private fun ReaderStepperRow(
    title: String,
    valueText: String,
    themePalette: ReaderThemePalette,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title：$valueText"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.bodyMedium,
            color = themePalette.content,
        )
        ReaderInlinePill(
            label = "A-",
            themePalette = themePalette,
            modifier = Modifier.weight(0.8f),
            onClick = onDecrease,
        )
        Text(
            text = valueText,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = themePalette.content,
        )
        ReaderInlinePill(
            label = "A+",
            themePalette = themePalette,
            modifier = Modifier.weight(0.8f),
            onClick = onIncrease,
        )
    }
}

@Composable
private fun ReaderModeRow(
    readingMode: ReadingMode,
    settings: ReaderSettings,
    themePalette: ReaderThemePalette,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "阅读模式",
            style = MaterialTheme.typography.bodyMedium,
            color = themePalette.content,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderInlinePill(
                label = "滚动",
                themePalette = themePalette,
                modifier = Modifier.weight(1f),
                selected = readingMode == ReadingMode.SCROLL,
                contentDescription = "阅读模式：滚动，${if (readingMode == ReadingMode.SCROLL) "已选中" else "未选中"}",
                onClick = {
                    onUpdateSettings(settings.copy(readingMode = ReadingMode.SCROLL))
                },
            )
            ReaderInlinePill(
                label = "翻页",
                themePalette = themePalette,
                modifier = Modifier.weight(1f),
                selected = readingMode == ReadingMode.PAGE,
                contentDescription = "阅读模式：翻页，${if (readingMode == ReadingMode.PAGE) "已选中" else "未选中"}",
                onClick = {
                    onUpdateSettings(settings.copy(readingMode = ReadingMode.PAGE))
                },
            )
        }
    }
}

@Composable
private fun ReaderThemeRow(
    appearanceMode: ReaderAppearanceMode,
    selectedPreset: ReaderThemePreset,
    themePalette: ReaderThemePalette,
    onSelectPreset: (ReaderThemePreset) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "颜色",
            style = MaterialTheme.typography.bodyMedium,
            color = themePalette.content,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderThemeResolver.presetsFor(appearanceMode).forEach { preset ->
                ReaderThemeSwatch(
                    preset = preset,
                    selected = preset == selectedPreset,
                    themePalette = themePalette,
                    onClick = { onSelectPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun ReaderThemeSwatch(
    preset: ReaderThemePreset,
    selected: Boolean,
    themePalette: ReaderThemePalette,
    onClick: () -> Unit,
) {
    val palette = preset.palette()
    Surface(
        modifier = Modifier
            .size(30.dp)
            .semantics {
                contentDescription = if (selected) {
                    "当前主题：${preset.label}"
                } else {
                    "切换主题：${preset.label}"
                }
            },
        shape = CircleShape,
        color = palette.background,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = themePalette.content.copy(alpha = if (selected) 0.78f else 0.28f),
        ),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(CircleShape)
                .background(palette.background),
        )
    }
}

@Composable
internal fun ReaderInlinePill(
    label: String,
    themePalette: ReaderThemePalette,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentDescription: String? = null,
    horizontalPadding: Dp = 0.dp,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        ),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            themePalette.content.copy(alpha = 0.14f)
        } else {
            themePalette.background.copy(alpha = 0.85f)
        },
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) themePalette.content else themePalette.content.copy(alpha = 0.85f),
        )
    }
}

private fun Float.formatOneDecimal(): String = String.format("%.1f", this)
