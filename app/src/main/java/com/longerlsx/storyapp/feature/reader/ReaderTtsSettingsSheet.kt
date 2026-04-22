package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    availableVoices: List<ReaderTtsVoiceOption>,
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
        )
        if (!remainingTimeLabel.isNullOrBlank()) {
            Text(
                text = "剩余时间：$remainingTimeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!systemDefaultVoiceStatus.isNullOrBlank()) {
            Text(
                text = systemDefaultVoiceStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (availableVoices.isEmpty()) {
            Text(
                text = "已筛除非大陆中文音色，当前将使用系统默认音色。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ReaderTtsChoiceSection(
                title = "音色",
                supportingText = "直接展示系统 TTS engine 当前可用的 voice。",
            ) {
                availableVoices.forEach { option ->
                    ReaderTtsChoicePill(
                        label = option.displayName,
                        selected = selectedVoiceName == option.name,
                        onClick = { onSelectVoice(option.name) },
                    )
                }
            }
        }
        ReaderTtsChoiceSection(
            title = "语速",
            supportingText = "修改后从下一个短句块开始生效。",
        ) {
            SpeechRateOptions.forEach { option ->
                ReaderTtsChoicePill(
                    label = "${option.formatOneDecimal()}x",
                    selected = settings.speechRate.nearlyEquals(option),
                    onClick = { onUpdateSpeechRate(option) },
                )
            }
        }
        ReaderTtsChoiceSection(
            title = "音高",
            supportingText = "机械感更强时，通常会偏低一点。",
        ) {
            PitchOptions.forEach { option ->
                ReaderTtsChoicePill(
                    label = option.formatOneDecimal(),
                    selected = settings.pitch.nearlyEquals(option),
                    onClick = { onUpdatePitch(option) },
                )
            }
        }
        ReaderTtsChoiceSection(
            title = "定时",
            supportingText = "新会话会沿用上次选择的定时偏好。",
        ) {
            TimerPresetOptions.forEach { preset ->
                ReaderTtsChoicePill(
                    label = preset.displayLabel(),
                    selected = settings.timerPreset == preset,
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
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun ReaderTtsTimerPreset.displayLabel(): String {
    return when (this) {
        ReaderTtsTimerPreset.NoTimer -> "不定时"
        is ReaderTtsTimerPreset.Countdown -> "$minutes 分钟"
    }
}

private fun Float.formatOneDecimal(): String = String.format("%.1f", this)

private fun Float.nearlyEquals(other: Float): Boolean = kotlin.math.abs(this - other) < 0.001f
