package com.longerlsx.storyapp.feature.bookshelf

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.longerlsx.storyapp.feature.source.SourceEntryScreen
import org.junit.Rule
import org.junit.Test

class BookshelfEntryCopyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyBookshelfUsesUserFacingCopyInsteadOfImplementationProgress() {
        composeRule.setContent {
            BookshelfScreen(
                books = emptyList(),
                onImportTxt = {},
                onOpenSourceEntry = {},
                onOpenBook = {},
            )
        }

        composeRule.onNodeWithText("还没有导入书籍").assertIsDisplayed()
        composeRule.onNodeWithText("导入本地 TXT 后会在这里继续阅读。").assertIsDisplayed()
        composeRule
            .onAllNodesWithText("当前版本已经打通文件复制、解析、入书架和基础阅读页。")
            .assertCountEquals(0)
    }

    @Test
    fun sourceEntryUsesUnavailableStateInsteadOfImplementationPlaceholder() {
        composeRule.setContent {
            SourceEntryScreen(onBack = {})
        }

        composeRule.onNodeWithText("在线书源暂未开放").assertIsDisplayed()
        composeRule.onNodeWithText("当前可以先导入本地 TXT 阅读。").assertIsDisplayed()
        composeRule
            .onAllNodesWithText("书源能力已预留，首期暂不接入真实网络源。")
            .assertCountEquals(0)
    }
}
