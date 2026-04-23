package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import kotlin.math.roundToInt

data class ReaderTtsToggleUiState(
    val actionLabel: String = "朗读",
    val showImmersiveAction: Boolean = false,
    val immersiveActionLabel: String = actionLabel,
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
    expandedContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = themePalette.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 10.dp,
        shadowElevation = 10.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Keep the bottom action bar visible even when settings/directory content grows tall.
            val expandedPanelMaxHeight = maxOf(
                220.dp,
                minOf(maxHeight * 0.68f, maxHeight - 92.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (chromeMode) {
                    ReaderChromeMode.CHROME_VISIBLE -> {
                        if (showChapterNavigationRow) {
                            ReaderChapterNavigationRow(
                                progressSummary = progressSummary,
                                themePalette = themePalette,
                                canOpenPreviousChapter = canOpenPreviousChapter,
                                canOpenNextChapter = canOpenNextChapter,
                                onOpenPreviousChapter = onOpenPreviousChapter,
                                onOpenNextChapter = onOpenNextChapter,
                            )
                        }
                    }

                    ReaderChromeMode.SETTINGS_EXPANDED,
                    ReaderChromeMode.DIRECTORY_OPEN,
                    -> {
                        if (expandedContent != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = expandedPanelMaxHeight),
                                color = themePalette.surface.copy(alpha = 0.98f),
                                shape = RoundedCornerShape(24.dp),
                                tonalElevation = 2.dp,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                ) {
                                    expandedContent()
                                }
                            }
                        }
                    }

                    ReaderChromeMode.READING_ONLY -> {
                        if (ttsToggleState?.showImmersiveAction == true) {
                            ReaderImmersiveTtsStopRow(
                                themePalette = themePalette,
                                label = ttsToggleState.immersiveActionLabel,
                                onActionClick = onImmersiveTtsAction,
                            )
                        }
                    }
                }

                when (chromeMode) {
                    ReaderChromeMode.CHROME_VISIBLE,
                    ReaderChromeMode.SETTINGS_EXPANDED,
                    ReaderChromeMode.DIRECTORY_OPEN,
                    -> ReaderPrimaryActionBar(
                        appearanceMode = appearanceMode,
                        themePalette = themePalette,
                        ttsToggleState = ttsToggleState,
                        onOpenToc = onOpenToc,
                        onToggleAppearanceMode = onToggleAppearanceMode,
                        onOpenSettings = onOpenSettings,
                        onToggleTts = onToggleTts,
                    )

                    ReaderChromeMode.READING_ONLY -> Unit
                }
            }
        }
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
            .padding(start = 14.dp, top = 14.dp, end = 18.dp)
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
            .padding(horizontal = 14.dp, vertical = 12.dp)
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
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            ReaderTopBarGlyphButton(
                glyph = "⋯",
                contentDescription = "更多",
                tint = themePalette.content.copy(alpha = 0.9f),
                onClick = {},
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
            .size(32.dp)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderBarAction(
            label = "上一章",
            enabled = canOpenPreviousChapter,
            themePalette = themePalette,
            onClick = onOpenPreviousChapter,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = progressSummary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = themePalette.content.copy(alpha = 0.8f),
        )
        ReaderBarAction(
            label = "下一章",
            enabled = canOpenNextChapter,
            themePalette = themePalette,
            onClick = onOpenNextChapter,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReaderPrimaryActionBar(
    appearanceMode: ReaderAppearanceMode,
    themePalette: ReaderThemePalette,
    ttsToggleState: ReaderTtsToggleUiState?,
    onOpenToc: () -> Unit,
    onToggleAppearanceMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTts: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderBarAction(
            label = "目录",
            themePalette = themePalette,
            onClick = onOpenToc,
            modifier = Modifier.weight(1f),
        )
        ReaderBarAction(
            label = if (appearanceMode == ReaderAppearanceMode.DAY) "夜间" else "日间",
            themePalette = themePalette,
            onClick = onToggleAppearanceMode,
            modifier = Modifier.weight(1f),
        )
        ReaderBarAction(
            label = "设置",
            themePalette = themePalette,
            onClick = onOpenSettings,
            modifier = Modifier.weight(1f),
        )
        if (ttsToggleState != null) {
            ReaderTtsToggleAction(
                state = ttsToggleState,
                themePalette = themePalette,
                onClick = onToggleTts,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReaderBarAction(
    label: String,
    themePalette: ReaderThemePalette,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) {
                    themePalette.background.copy(alpha = 0.9f)
                } else {
                    themePalette.background.copy(alpha = 0.45f)
                },
            )
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = null,
            )
            .semantics { contentDescription = label },
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (enabled) {
                themePalette.content
            } else {
                themePalette.content.copy(alpha = 0.35f)
            },
        )
    }
}

@Composable
private fun ReaderTtsToggleAction(
    state: ReaderTtsToggleUiState,
    themePalette: ReaderThemePalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(themePalette.background.copy(alpha = 0.9f))
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = null,
            )
            .semantics { contentDescription = state.actionLabel },
    ) {
        Text(
            text = state.actionLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = themePalette.content,
        )
    }
}

@Composable
private fun ReaderImmersiveTtsStopRow(
    themePalette: ReaderThemePalette,
    label: String,
    onActionClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderBarAction(
            label = label,
            themePalette = themePalette,
            onClick = onActionClick,
            modifier = Modifier.weight(1f),
        )
    }
}
