package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsSpeechRates
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import java.util.Locale

private val PitchOptions = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f)
private val CommonTimerPresets = listOf(
    ReaderTtsTimerPreset.NoTimer,
    ReaderTtsTimerPreset.Countdown(15),
    ReaderTtsTimerPreset.Countdown(30),
    ReaderTtsTimerPreset.Countdown(60),
)

/** Playback controls stay fixed while only the settings body scrolls on short screens. */
@Composable
fun ReaderTtsSettingsSheet(
    statusText: String,
    settings: ReaderTtsSettings,
    themePalette: ReaderThemePalette,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onStop: (() -> Unit)?,
    onClose: () -> Unit,
    onUpdateSpeechRate: (Float) -> Unit,
    onUpdatePitch: (Float) -> Unit,
    onUpdateTimerPreset: (ReaderTtsTimerPreset) -> Unit,
    remainingTimeLabel: String? = null,
    errorText: String? = null,
) {
    var pitchExpanded by rememberSaveable { mutableStateOf(false) }
    var timerMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().semantics { contentDescription = "听书面板" }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("听书", style = MaterialTheme.typography.titleLarge, color = themePalette.content)
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = themePalette.subtleContent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ReaderTtsIconButton(primaryActionLabel, themePalette, onPrimaryAction, primary = true)
            ReaderTtsIconButton("停止朗读", themePalette, onStop, outlined = true)
            ReaderTtsIconButton("收起听书面板", themePalette, onClose)
        }
        HorizontalDivider(color = themePalette.outline)
        Column(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!errorText.isNullOrBlank()) {
                Text(errorText, style = MaterialTheme.typography.bodyMedium, color = themePalette.content)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTtsSectionHeading("定时关闭", themePalette,
                    remainingTimeLabel?.let { "剩余 $it" } ?: settings.timerPreset.fullLabel())
                ReaderTtsOptionRow { itemWidth ->
                    CommonTimerPresets.forEach { preset ->
                        ReaderTtsChoicePill(
                            label = preset.shortLabel(),
                            description = preset.fullLabel(),
                            selected = settings.timerPreset == preset,
                            themePalette = themePalette,
                            modifier = Modifier.width(itemWidth),
                            onClick = { if (settings.timerPreset != preset) onUpdateTimerPreset(preset) },
                        )
                    }
                    val extraPreset = (settings.timerPreset as? ReaderTtsTimerPreset.Countdown)
                        ?.takeIf { it.minutes > 60 }
                    Box(Modifier.width(itemWidth)) {
                        ReaderTtsChoicePill(
                            label = extraPreset?.shortLabel() ?: "更多",
                            description = "更多定时时长",
                            selected = extraPreset != null,
                            themePalette = themePalette,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { timerMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = timerMenuExpanded,
                            onDismissRequest = { timerMenuExpanded = false },
                            containerColor = themePalette.surface,
                        ) {
                            listOf(90, 120).forEach { minutes ->
                                val preset = ReaderTtsTimerPreset.Countdown(minutes)
                                DropdownMenuItem(
                                    text = { Text(preset.fullLabel(), color = themePalette.content) },
                                    modifier = Modifier.semantics { selected = settings.timerPreset == preset },
                                    onClick = {
                                        timerMenuExpanded = false
                                        if (settings.timerPreset != preset) onUpdateTimerPreset(preset)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTtsSectionHeading("语速", themePalette, ReaderTtsSpeechRates.label(settings.speechRate))
                ReaderTtsOptionRow { itemWidth ->
                    ReaderTtsSpeechRates.options.forEach { option ->
                        ReaderTtsChoicePill(
                            label = ReaderTtsSpeechRates.label(option),
                            selected = settings.speechRate.nearlyEquals(option),
                            themePalette = themePalette,
                            modifier = Modifier.width(itemWidth),
                            onClick = { if (!settings.speechRate.nearlyEquals(option)) onUpdateSpeechRate(option) },
                        )
                    }
                }
            }
            ReaderTtsSectionHeading("音色", themePalette, "雷军 · 合成音色")
            Column {
                HorizontalDivider(color = themePalette.outline)
                Surface(
                    onClick = { pitchExpanded = !pitchExpanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = if (pitchExpanded) "收起更多选项" else "展开更多选项"
                        stateDescription = if (pitchExpanded) "已展开" else "已收起"
                    },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("更多选项", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = themePalette.content)
                        ReaderActionIcon(if (pitchExpanded) "向上" else "向下", themePalette.subtleContent, Modifier.size(20.dp))
                    }
                }
                if (pitchExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTtsSectionHeading("音调", themePalette, settings.pitch.formatOneDecimal())
                        ReaderTtsOptionRow { itemWidth ->
                            PitchOptions.forEach { option ->
                                ReaderTtsChoicePill(option.formatOneDecimal(), settings.pitch.nearlyEquals(option), themePalette,
                                    modifier = Modifier.width(itemWidth),
                                    onClick = { if (!settings.pitch.nearlyEquals(option)) onUpdatePitch(option) })
                            }
                        }
                    }
                }
            }
            Text("调整自动保存，语速与音调从下一句生效", style = MaterialTheme.typography.bodySmall, color = themePalette.subtleContent)
        }
    }
}

@Composable
private fun ReaderTtsSectionHeading(title: String, themePalette: ReaderThemePalette, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = themePalette.content)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = themePalette.subtleContent)
    }
}

@Composable
private fun ReaderTtsOptionRow(content: @Composable (Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Keep 48dp targets; larger text wraps the options instead of shrinking their labels.
        val minimumWidth = (50 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
        val columns = ((maxWidth + 6.dp) / (minimumWidth + 6.dp)).toInt().coerceIn(1, 5)
        val itemWidth = (maxWidth - 6.dp * (columns - 1)) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content(itemWidth)
        }
    }
}

@Composable
private fun ReaderTtsChoicePill(
    label: String,
    selected: Boolean,
    themePalette: ReaderThemePalette,
    modifier: Modifier = Modifier,
    description: String = label,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics {
            contentDescription = description
            this.selected = selected
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) themePalette.accent else themePalette.outline),
        color = if (selected) themePalette.accent.copy(alpha = 0.1f) else themePalette.background,
    ) {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) themePalette.accent else themePalette.content,
                textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

private fun ReaderTtsTimerPreset.shortLabel(): String = when (this) {
    ReaderTtsTimerPreset.NoTimer -> "关闭"
    is ReaderTtsTimerPreset.Countdown -> "${minutes}分"
}

private fun ReaderTtsTimerPreset.fullLabel(): String = when (this) {
    ReaderTtsTimerPreset.NoTimer -> "不定时"
    is ReaderTtsTimerPreset.Countdown -> "$minutes 分钟"
}

private fun Float.formatOneDecimal(): String = String.format(Locale.ROOT, "%.1f", this)
private fun Float.nearlyEquals(other: Float): Boolean = kotlin.math.abs(this - other) < 0.001f
