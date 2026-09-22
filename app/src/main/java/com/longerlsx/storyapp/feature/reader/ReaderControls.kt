package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderTtsSpeechRates
import kotlin.math.roundToInt

data class ReaderTtsToggleUiState(
    val actionLabel: String = "朗读",
    val showImmersiveAction: Boolean = false,
    val immersiveActionLabel: String = actionLabel,
    val statusText: String = "",
    val remainingTimeLabel: String? = null,
    val speechRate: Float = 1f,
)

@Composable
fun ReaderControls(
    chromeMode: ReaderChromeMode,
    appearanceMode: ReaderAppearanceMode,
    progressSummary: String,
    showChapterNavigationRow: Boolean,
    canOpenPreviousChapter: Boolean,
    canOpenNextChapter: Boolean,
    themePalette: ReaderThemePalette,
    ttsToggleState: ReaderTtsToggleUiState? = null,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleAppearanceMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTts: () -> Unit = {},
    onImmersiveTtsAction: () -> Unit = {},
    onOpenListening: () -> Unit = {},
    onOpenListeningSettings: () -> Unit = {},
    onStopTts: (() -> Unit)? = null,
    onCloseExpanded: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = themePalette.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 3.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // This is an overlay. Its height never contributes to the text viewport.
            val expandedPanelMaxHeight = (maxHeight - 144.dp).coerceIn(48.dp, 620.dp)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (ttsToggleState?.showImmersiveAction == true &&
                    (chromeMode == ReaderChromeMode.READING_ONLY || chromeMode == ReaderChromeMode.CHROME_VISIBLE)) {
                    ReaderTtsCompactBar(ttsToggleState, themePalette, onOpenListening, onImmersiveTtsAction, onStopTts)
                }
                when (chromeMode) {
                    ReaderChromeMode.CHROME_VISIBLE -> if (showChapterNavigationRow) {
                        ReaderChapterNavigationRow(progressSummary, themePalette,
                            canOpenPreviousChapter, canOpenNextChapter,
                            onOpenPreviousChapter, onOpenNextChapter)
                    }
                    ReaderChromeMode.SETTINGS_EXPANDED, ReaderChromeMode.DIRECTORY_OPEN -> {
                        if (onCloseExpanded != null) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (chromeMode == ReaderChromeMode.DIRECTORY_OPEN) "目录" else "阅读设置",
                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                    style = MaterialTheme.typography.titleSmall, color = themePalette.subtleContent)
                                ReaderTopBarGlyphButton("×", "关闭面板", themePalette.content, onCloseExpanded)
                            }
                        }
                        Box(Modifier.fillMaxWidth().heightIn(max = expandedPanelMaxHeight)
                            .padding(horizontal = 4.dp, vertical = 8.dp)) { expandedContent?.invoke() }
                    }
                    ReaderChromeMode.LISTENING_EXPANDED -> {
                        Box(Modifier.fillMaxWidth().heightIn(max = expandedPanelMaxHeight)
                            .padding(horizontal = 4.dp, vertical = 8.dp)) { expandedContent?.invoke() }
                    }
                    ReaderChromeMode.READING_ONLY -> Unit
                }
                if (chromeMode != ReaderChromeMode.READING_ONLY) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ReaderBarAction("目录", themePalette, Modifier.weight(1f), icon = true, onClick = onOpenToc)
                        ReaderBarAction(if (appearanceMode == ReaderAppearanceMode.DAY) "夜间" else "日间",
                            themePalette, Modifier.weight(1f), icon = true, onClick = onToggleAppearanceMode)
                        ReaderBarAction("设置", themePalette, Modifier.weight(1f), icon = true, onClick = onOpenSettings)
                        if (ttsToggleState != null) ReaderBarAction(ttsToggleState.actionLabel, themePalette,
                            Modifier.weight(1f), icon = true,
                            onLongClick = onOpenListeningSettings,
                            onClick = onToggleTts)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTtsCompactBar(
    state: ReaderTtsToggleUiState,
    themePalette: ReaderThemePalette,
    onOpenListening: () -> Unit,
    onPlaybackAction: () -> Unit,
    onStop: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onOpenListening)
                .semantics(mergeDescendants = true) { contentDescription = "展开听书面板" }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.statusText, style = MaterialTheme.typography.bodyMedium,
                    color = themePalette.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(state.remainingTimeLabel?.let { "剩余 $it" },
                    ReaderTtsSpeechRates.label(state.speechRate)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = themePalette.subtleContent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ReaderActionIcon("向上", themePalette.subtleContent, Modifier.size(20.dp))
        }
        ReaderTtsIconButton(state.immersiveActionLabel, themePalette, onPlaybackAction, primary = true)
        ReaderTtsIconButton("停止朗读", themePalette, onStop, outlined = true)
    }
}

/** A stable 48dp target shared by the full listening panel and its compact bar. */
@Composable
internal fun ReaderTtsIconButton(
    label: String,
    themePalette: ReaderThemePalette,
    onClick: (() -> Unit)?,
    primary: Boolean = false,
    outlined: Boolean = false,
) {
    val enabled = onClick != null
    val tint = (if (primary) themePalette.onAccent else themePalette.content)
        .copy(alpha = if (enabled) 1f else 0.38f)
    Surface(
        onClick = onClick ?: {},
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label; role = Role.Button },
        shape = if (primary) CircleShape else RoundedCornerShape(16.dp),
        color = if (primary) themePalette.accent else androidx.compose.ui.graphics.Color.Transparent,
        border = if (outlined) BorderStroke(1.dp, themePalette.outline) else null,
    ) {
        Box(contentAlignment = Alignment.Center) { ReaderActionIcon(label, tint, Modifier.size(24.dp)) }
    }
}

@Composable
fun BoxScope.ReaderImmersiveHeader(
    chapterTitle: String,
    themePalette: ReaderThemePalette,
    onBack: () -> Unit,
    onBottomMeasured: (Int) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            // 4 + 48 = the existing 52dp body start. Do not steal the first line's touches.
            .padding(start = 14.dp, top = 4.dp, end = 18.dp)
            .onGloballyPositioned { coordinates ->
                onBottomMeasured(coordinates.positionInParent().y.plus(coordinates.size.height).roundToInt())
            }
            .semantics { contentDescription = "沉浸式阅读头部" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderTopBarGlyphButton(
            glyph = "‹",
            contentDescription = "返回",
            tint = themePalette.content.copy(alpha = 0.72f),
            onClick = onBack,
        )
        Text(
            text = chapterTitle,
            modifier = Modifier
                .padding(start = 8.dp)
                .semantics { contentDescription = "沉浸式章节：$chapterTitle" },
            color = themePalette.content.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BoxScope.ReaderTopBar(
    bookTitle: String,
    chapterTitle: String,
    themePalette: ReaderThemePalette,
    onBack: () -> Unit,
    onBottomMeasured: (Int) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .onGloballyPositioned { coordinates ->
                onBottomMeasured(coordinates.positionInParent().y.plus(coordinates.size.height).roundToInt())
            }
            .semantics { contentDescription = "阅读器顶部栏" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderTopBarGlyphButton(
                glyph = "‹",
                contentDescription = "返回",
                tint = themePalette.content,
                onClick = onBack,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .semantics { contentDescription = "顶部栏标题：$bookTitle $chapterTitle" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = bookTitle,
                color = themePalette.content.copy(alpha = 0.94f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chapterTitle,
                modifier = Modifier.padding(top = 2.dp),
                color = themePalette.content.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReaderTopBarGlyphButton(
    glyph: String,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReaderChapterNavigationRow(
    progressSummary: String,
    themePalette: ReaderThemePalette,
    canOpenPreviousChapter: Boolean,
    canOpenNextChapter: Boolean,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ReaderBarAction("上一章", themePalette, Modifier.weight(1f), canOpenPreviousChapter,
            onClick = onOpenPreviousChapter)
        Text(progressSummary, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            color = themePalette.subtleContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ReaderBarAction("下一章", themePalette, Modifier.weight(1f), canOpenNextChapter,
            onClick = onOpenNextChapter)
    }
}

@Composable
private fun ReaderBarAction(
    label: String,
    themePalette: ReaderThemePalette,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Boolean = false,
    displayLabel: String = label,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (enabled) themePalette.content else themePalette.content.copy(alpha = 0.38f)
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .combinedClickable(enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .heightIn(min = if (icon) 64.dp else 48.dp)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon) ReaderActionIcon(label, tint, Modifier.size(24.dp))
        Text(displayLabel, color = tint,
            modifier = Modifier.padding(top = if (icon) 4.dp else 0.dp),
            style = if (icon) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, maxLines = if (icon && displayLabel.length <= 4) 2 else 1,
            overflow = TextOverflow.Ellipsis)
    }
}
