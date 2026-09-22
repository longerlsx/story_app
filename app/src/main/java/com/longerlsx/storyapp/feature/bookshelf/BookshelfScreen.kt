package com.longerlsx.storyapp.feature.bookshelf

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.longerlsx.storyapp.R
import com.longerlsx.storyapp.core.model.Book
import java.util.Locale

@Suppress("UNUSED_PARAMETER", "DEPRECATION")
@Composable
fun BookshelfScreen(
    books: List<Book>,
    onImportTxt: () -> Unit,
    onOpenSourceEntry: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val view = LocalView.current
    SideEffect {
        if (view.isInEditMode) return@SideEffect
        var context = view.context
        while (context is ContextWrapper && context !is Activity) {
            val baseContext = context.baseContext
            if (baseContext === context) return@SideEffect
            context = baseContext
        }
        val window = (context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        val useDarkIcons = background.luminance() > 0.5f
        // Only the visible bookshelf owns this effect; ReaderScreen manages its own bars.
        window.statusBarColor = background.toArgb()
        window.navigationBarColor = background.toArgb()
        insetsController.isAppearanceLightStatusBars = useDarkIcons
        insetsController.isAppearanceLightNavigationBars = useDarkIcons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    Scaffold(containerColor = background) { padding ->
        BookshelfContent(
            books = books,
            padding = padding,
            onImportTxt = onImportTxt,
            onOpenBook = onOpenBook,
        )
    }
}

@Composable
private fun BookshelfContent(
    books: List<Book>,
    padding: PaddingValues,
    onImportTxt: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    ) {
        item {
            BookshelfHeader(onImportTxt = onImportTxt)
        }

        if (books.isEmpty()) {
            item {
                EmptyBookshelf()
            }
        } else {
            itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                BookshelfBookRow(
                    book = book,
                    onClick = { onOpenBook(book.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookshelfHeader(onImportTxt: () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "书架",
                modifier = Modifier
                    .padding(end = 16.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
            )
            Button(
                onClick = onImportTxt,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                ImportIcon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "导入 TXT", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            text = "本地 TXT · 按最近阅读",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportIcon() {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(20.dp)) {
        val inset = 3.dp.toPx()
        drawLine(
            color = color,
            start = Offset(inset, center.y),
            end = Offset(size.width - inset, center.y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(center.x, inset),
            end = Offset(center.x, size.height - inset),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun EmptyBookshelf() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "还没有导入书籍",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "导入本地 TXT 后会在这里继续阅读。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookshelfBookRow(
    book: Book,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BookTextCover(book = book)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!book.author.isNullOrBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${book.chapterCount} 章 · ${formatWordCount(book.wordCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BookTextCover(book: Book) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (Math.floorMod(book.title.hashCode(), 3)) {
        0 -> colors.primary to colors.onPrimary
        1 -> colors.tertiary to colors.onTertiary
        else -> colors.secondary to colors.onSecondary
    }
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clearAndSetSemantics {},
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(content.copy(alpha = 0.15f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(
                modifier = Modifier.width(20.dp),
                color = content.copy(alpha = 0.65f),
            )
        }
    }
}

private fun formatWordCount(wordCount: Int): String =
    if (wordCount >= 10_000) {
        String.format(Locale.CHINA, "%.1f 万字", wordCount / 10_000.0)
    } else {
        "$wordCount 字"
    }
