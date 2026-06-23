package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption

private val SpeechRateOptions = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.45f)
private val PitchOptions = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f)
private val TimerPresetOptions = listOf(
    ReaderTtsTimerPreset.NoTimer,
    ReaderTtsTimerPreset.Countdown(15),
    ReaderTtsTimerPreset.Countdown(30),
    ReaderTtsTimerPreset.Countdown(60),
    ReaderTtsTimerPreset.Countdown(90),
    ReaderTtsTimerPreset.Countdown(120),
)

@Composable
fun ReaderTtsSettingsSheet(
    statusText: String,
    settings: ReaderTtsSettings,
    themePalette: ReaderThemePalette,
    availableVoices: List<ReaderTtsVoiceOption>,
    availableVoicesLoaded: Boolean = true,
    selectedVoiceName: String?,
    remainingTimeLabel: String? = null,
    systemDefaultVoiceStatus: String? = null,
    onSelectVoice: (String?) -> Unit,
    onUpdateSpeechRate: (Float) -> Unit,
    onUpdatePitch: (Float) -> Unit,
    onUpdateTimerPreset: (ReaderTtsTimerPreset) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = themePalette.content,
        )
        if (!remainingTimeLabel.isNullOrBlank()) {
            Text(
                text = "剩余时间：$remainingTimeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = themePalette.content.copy(alpha = 0.65f),
            )
        }
        if (availableVoicesLoaded && !systemDefaultVoiceStatus.isNullOrBlank()) {
            Text(
                text = systemDefaultVoiceStatus,
                style = MaterialTheme.typography.bodySmall,
                color = themePalette.content.copy(alpha = 0.65f),
            )
        }
        if (!availableVoicesLoaded) {
            Text(
                text = "正在加载系统音色...",
                style = MaterialTheme.typography.bodySmall,
                color = themePalette.content.copy(alpha = 0.65f),
            )
        } else if (availableVoices.isEmpty()) {
            Text(
                text = "已筛除非大陆中文音色，当前将使用系统默认音色。",
                style = MaterialTheme.typography.bodySmall,
                color = themePalette.content.copy(alpha = 0.65f),
            )
        } else {
            ReaderTtsChoiceSection(
                title = "音色",
                supportingText = "仅显示可用的大陆中文音色。",
                themePalette = themePalette,
            ) {
                availableVoices.forEach { option ->
                    ReaderTtsChoicePill(
                        label = option.displayName,
                        selected = selectedVoiceName == option.name,
                        themePalette = themePalette,
                        onClick = { onSelectVoice(option.name) },
                    )
                }
            }
        }
        ReaderTtsChoiceSection(
            title = "语速",
            supportingText = "调整后会在后续朗读中生效。",
            themePalette = themePalette,
        ) {
            SpeechRateOptions.forEach { option ->
                ReaderTtsChoicePill(
                    label = "${option.formatOneDecimal()}x",
                    selected = settings.speechRate.nearlyEquals(option),
                    themePalette = themePalette,
                    onClick = { onUpdateSpeechRate(option) },
                )
            }
        }
        ReaderTtsChoiceSection(
            title = "音高",
            supportingText = "调低会更沉稳，调高会更清亮。",
            themePalette = themePalette,
        ) {
            PitchOptions.forEach { option ->
                ReaderTtsChoicePill(
                    label = option.formatOneDecimal(),
                    selected = settings.pitch.nearlyEquals(option),
                    themePalette = themePalette,
                    onClick = { onUpdatePitch(option) },
                )
            }
        }
        ReaderTtsChoiceSection(
            title = "定时",
            supportingText = "下次朗读会继续使用这个选择。",
            themePalette = themePalette,
        ) {
            TimerPresetOptions.forEach { preset ->
                ReaderTtsChoicePill(
                    label = preset.displayLabel(),
                    selected = settings.timerPreset == preset,
                    themePalette = themePalette,
                    onClick = { onUpdateTimerPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun ReaderTtsChoiceSection(
    title: String,
    supportingText: String? = null,
    themePalette: ReaderThemePalette,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = themePalette.content,
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = themePalette.content.copy(alpha = 0.65f),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ReaderTtsChoicePill(
    label: String,
    selected: Boolean,
    themePalette: ReaderThemePalette,
    onClick: () -> Unit,
) {
    ReaderInlinePill(
        label = label,
        themePalette = themePalette,
        selected = selected,
        horizontalPadding = 14.dp,
        onClick = onClick,
    )
}

private fun ReaderTtsTimerPreset.displayLabel(): String {
    return when (this) {
        ReaderTtsTimerPreset.NoTimer -> "不定时"
        is ReaderTtsTimerPreset.Countdown -> "$minutes 分钟"
    }
}

private fun Float.formatOneDecimal(): String = String.format("%.1f", this)

private fun Float.nearlyEquals(other: Float): Boolean = kotlin.math.abs(this - other) < 0.001f
