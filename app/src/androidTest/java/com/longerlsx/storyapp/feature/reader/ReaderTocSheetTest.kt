package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.Chapter
import org.junit.Rule
import org.junit.Test

class ReaderTocSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longDirectoryOpensWithCurrentChapterVisible() {
        val chapters = (1..80).map { chapterNumber ->
            Chapter(
                bookId = "book",
                chapterIndex = chapterNumber,
                title = "第${chapterNumber}章",
                startOffset = chapterNumber * 100,
                endOffset = chapterNumber * 100 + 80,
                wordCount = 80,
            )
        }

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.height(220.dp)) {
                    ReaderTocSheet(
                        bookTitle = "长目录测试",
                        chapters = chapters,
                        selectedChapterIndex = 70,
                        themePalette = ReaderThemePalette(
                            background = Color.White,
                            surface = Color(0xFFF4F4F4),
                            content = Color.Black,
                        ),
                        onSelectChapter = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("第70章").assertIsDisplayed()
        composeRule.onNodeWithText("当前阅读").assertIsDisplayed()
    }
}
