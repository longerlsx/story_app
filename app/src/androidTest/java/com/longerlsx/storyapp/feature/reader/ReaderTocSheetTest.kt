package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.Chapter
import org.junit.Assert.assertTrue
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

    @Test
    fun longChapterTitleStaysSingleLineInDirectoryRow() {
        val longChapterTitle = "第84章 幸福玛丽孤儿院1：我愿意在你的胞宫中沉睡，直到世界的纱幕再一次掀起"
        val chapters = listOf(
            Chapter(
                bookId = "book",
                chapterIndex = 83,
                title = "第83章 平常标题",
                startOffset = 8_300,
                endOffset = 8_380,
                wordCount = 80,
            ),
            Chapter(
                bookId = "book",
                chapterIndex = 84,
                title = longChapterTitle,
                startOffset = 8_400,
                endOffset = 8_480,
                wordCount = 80,
            ),
            Chapter(
                bookId = "book",
                chapterIndex = 85,
                title = "第85章 下一章",
                startOffset = 8_500,
                endOffset = 8_580,
                wordCount = 80,
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(260.dp),
                ) {
                    ReaderTocSheet(
                        bookTitle = "真实长章节标题测试",
                        chapters = chapters,
                        selectedChapterIndex = 83,
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

        val longTitleBounds = composeRule
            .onNodeWithText(longChapterTitle)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val longTitleHeight = longTitleBounds.bottom - longTitleBounds.top
        val referenceTitleBounds = composeRule
            .onNodeWithText("第85章 下一章")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val referenceTitleHeight = referenceTitleBounds.bottom - referenceTitleBounds.top

        assertTrue(
            "TOC chapter titles should stay single-line; actual height=$longTitleHeight, reference single-line height=$referenceTitleHeight",
            longTitleHeight <= referenceTitleHeight,
        )
    }
}
