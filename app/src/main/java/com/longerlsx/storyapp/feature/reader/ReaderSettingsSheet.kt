package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingMode
import kotlin.math.roundToInt

@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    themePalette: ReaderThemePalette,
    onUpdateSettings: (ReaderSettings) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReaderReadingSettingsBody(settings, themePalette, onUpdateSettings)
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
    HorizontalDivider(color = themePalette.outline)

    ReaderStepperRow(
        title = "字号",
        valueText = settings.fontSizeSp.toString(),
        themePalette = themePalette,
        onDecrease = {
            onUpdateSettings(ReaderReadingSettingsAdjustmentPolicy.decreaseFontSize(settings))
        },
        onIncrease = {
            onUpdateSettings(ReaderReadingSettingsAdjustmentPolicy.increaseFontSize(settings))
        },
    )

    ReaderStepperRow(
        title = "行距",
        valueText = settings.lineHeightMultiplier.formatOneDecimal(),
        themePalette = themePalette,
        onDecrease = {
            onUpdateSettings(ReaderReadingSettingsAdjustmentPolicy.decreaseLineHeight(settings))
        },
        onIncrease = {
            onUpdateSettings(ReaderReadingSettingsAdjustmentPolicy.increaseLineHeight(settings))
        },
    )

    ReaderStepperRow(
        title = "段落距",
        valueText = settings.paragraphSpacingEm.formatOneDecimal(),
        themePalette = themePalette,
        onDecrease = {
            onUpdateSettings(ReaderReadingSettingsAdjustmentPolicy.decreaseParagraphSpacing(settings))
        },
        onIncrease = {
            onUpdateSettings(ReaderReadingSettingsAdjustmentPolicy.increaseParagraphSpacing(settings))
        },
    )

    HorizontalDivider(color = themePalette.outline)
    ReaderModeRow(
        readingMode = settings.readingMode,
        settings = settings,
        themePalette = themePalette,
        onUpdateSettings = onUpdateSettings,
    )

    HorizontalDivider(color = themePalette.outline)
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
                style = MaterialTheme.typography.titleSmall,
                color = themePalette.content,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = themePalette.subtleContent,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = themePalette.accent,
                activeTrackColor = themePalette.accent,
                inactiveTrackColor = themePalette.outline,
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title：$valueText" }) {
        val stackControls = maxWidth < (240 * LocalDensity.current.fontScale).dp
        val controls: @Composable (Modifier) -> Unit = { modifier ->
            Surface(
                modifier = modifier,
                color = Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, themePalette.outline),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReaderStepperButton(
                        label = if (title == "字号") "A-" else "−",
                        description = "减小$title",
                        themePalette = themePalette,
                        modifier = Modifier.weight(1f),
                        onClick = onDecrease,
                    )
                    Text(
                        text = valueText,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = themePalette.content,
                    )
                    ReaderStepperButton(
                        label = if (title == "字号") "A+" else "+",
                        description = "增大$title",
                        themePalette = themePalette,
                        modifier = Modifier.weight(1f),
                        onClick = onIncrease,
                    )
                }
            }
        }
        if (stackControls) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = themePalette.content)
                controls(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = themePalette.content)
                controls(Modifier.weight(1.8f))
            }
        }
    }
}

@Composable
private fun ReaderStepperButton(
    label: String,
    description: String,
    themePalette: ReaderThemePalette,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .semantics { contentDescription = description },
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = themePalette.content)
        }
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
                showSelectionIndicator = true,
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
                showSelectionIndicator = true,
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
            text = "页面颜色",
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
                    modifier = Modifier.weight(1f),
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
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val palette = preset.palette()
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = if (selected) {
                    "当前主题：${preset.label}"
                } else {
                    "切换主题：${preset.label}"
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = palette.background,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) themePalette.accent else themePalette.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) ReaderSelectionCheck(palette.content)
                }
            }
            Text(preset.label, style = MaterialTheme.typography.bodySmall, color = themePalette.content, textAlign = TextAlign.Center)
        }
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
    showSelectionIndicator: Boolean = false,
    singleLine: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { this.selected = selected }.then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) themePalette.accent else themePalette.outline),
        color = if (selected) {
            themePalette.accent.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding.coerceAtLeast(8.dp), vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (singleLine) {
                ReaderSingleLineText(label, MaterialTheme.typography.bodyMedium, if (selected) themePalette.accent else themePalette.content,
                    modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
            } else {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) themePalette.accent else themePalette.content,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
            }
            if (showSelectionIndicator) {
                if (selected) ReaderSelectionCheck(themePalette.accent)
                else Box(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun ReaderSelectionCheck(color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * 0.16f, size.height * 0.5f), Offset(size.width * 0.41f, size.height * 0.75f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.41f, size.height * 0.75f), Offset(size.width * 0.86f, size.height * 0.24f), stroke, StrokeCap.Round)
    }
}

private fun Float.formatOneDecimal(): String = String.format("%.1f", this)
