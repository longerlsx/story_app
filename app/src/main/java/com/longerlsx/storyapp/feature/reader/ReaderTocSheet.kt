package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.Chapter

@Composable
fun ReaderTocSheet(
    bookTitle: String,
    chapters: List<Chapter>,
    selectedChapterIndex: Int,
    themePalette: ReaderThemePalette,
    onSelectChapter: (Int) -> Unit,
) {
    val selectedChapterPosition = chapters
        .indexOfFirst { it.chapterIndex == selectedChapterIndex }
        .coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedChapterPosition)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = bookTitle,
            style = MaterialTheme.typography.titleMedium,
            color = themePalette.content,
        )
        Text(
            text = "共 ${chapters.size} 章",
            style = MaterialTheme.typography.bodySmall,
            color = themePalette.content.copy(alpha = 0.65f),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chapters, key = { it.chapterIndex }) { chapter ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = if (chapter.chapterIndex == selectedChapterIndex) {
                        themePalette.surface.copy(alpha = 0.96f)
                    } else {
                        themePalette.background.copy(alpha = 0.88f)
                    },
                    tonalElevation = if (chapter.chapterIndex == selectedChapterIndex) 2.dp else 0.dp,
                )
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChapter(chapter.chapterIndex) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (chapter.chapterIndex == selectedChapterIndex) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = themePalette.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (chapter.chapterIndex == selectedChapterIndex) {
                            Text(
                                text = "当前阅读",
                                color = themePalette.content.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
