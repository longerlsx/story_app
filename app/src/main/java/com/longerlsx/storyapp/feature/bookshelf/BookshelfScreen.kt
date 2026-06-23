package com.longerlsx.storyapp.feature.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    books: List<Book>,
    onImportTxt: () -> Unit,
    onOpenSourceEntry: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "书架")
                },
            )
        },
    ) { padding ->
        BookshelfContent(
            books = books,
            padding = padding,
            onImportTxt = onImportTxt,
            onOpenSourceEntry = onOpenSourceEntry,
            onOpenBook = onOpenBook,
        )
    }
}

@Composable
private fun BookshelfContent(
    books: List<Book>,
    padding: PaddingValues,
    onImportTxt: () -> Unit,
    onOpenSourceEntry: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onImportTxt,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "导入 TXT")
                }
                Button(
                    onClick = onOpenSourceEntry,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "在线书源")
                }
            }
        }

        if (books.isEmpty()) {
            item {
                EmptyBookshelfCard()
            }
        } else {
            items(books, key = { it.id }) { book ->
                BookShelfCard(
                    book = book,
                    onClick = { onOpenBook(book.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyBookshelfCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "还没有导入书籍",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "导入本地 TXT 后会在这里继续阅读。",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BookShelfCard(
    book: Book,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
            )
            if (!book.author.isNullOrBlank()) {
                Text(
                    text = "作者：${book.author}",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "章节数：${book.chapterCount}  字数：${book.wordCount}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
