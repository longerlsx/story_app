package com.longerlsx.storyapp.feature.reader

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderTtsControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chromeVisibleUsesExplicitStopLabelAndNoLegacyLongPressHint() {
        var toggleClicks = 0

        composeRule.setContent {
            ReaderControls(
                chromeMode = ReaderChromeMode.CHROME_VISIBLE,
                appearanceMode = ReaderAppearanceMode.DAY,
                progressSummary = "1/1",
                showChapterNavigationRow = false,
                canOpenPreviousChapter = false,
                canOpenNextChapter = false,
                themePalette = ReaderThemePalette(
                    background = androidx.compose.ui.graphics.Color.White,
                    surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                    content = androidx.compose.ui.graphics.Color.Black,
                ),
                ttsToggleState = ReaderTtsToggleUiState(actionLabel = "停止朗读 · 28m"),
                onOpenPreviousChapter = {},
                onOpenNextChapter = {},
                onOpenToc = {},
                onToggleAppearanceMode = {},
                onOpenSettings = {},
                onToggleTts = { toggleClicks += 1 },
            )
        }

        composeRule.onAllNodesWithContentDescription("停止朗读 · 28m").assertCountEquals(1)
        composeRule.onAllNodesWithText("长按设置").assertCountEquals(0)
        composeRule.onNodeWithText("停止朗读 · 28m").assertIsDisplayed().performClick()

        assertEquals(1, toggleClicks)
    }

    @Test
    fun settingsExpandedKeepsBottomBarMountedWithTtsAction() {
        composeRule.setContent {
            ReaderControls(
                chromeMode = ReaderChromeMode.SETTINGS_EXPANDED,
                appearanceMode = ReaderAppearanceMode.DAY,
                progressSummary = "1/1",
                showChapterNavigationRow = false,
                canOpenPreviousChapter = false,
                canOpenNextChapter = false,
                themePalette = ReaderThemePalette(
                    background = androidx.compose.ui.graphics.Color.White,
                    surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                    content = androidx.compose.ui.graphics.Color.Black,
                ),
                ttsToggleState = ReaderTtsToggleUiState(actionLabel = "朗读"),
                onOpenPreviousChapter = {},
                onOpenNextChapter = {},
                onOpenToc = {},
                onToggleAppearanceMode = {},
                onOpenSettings = {},
                expandedContent = {
                    Text("统一设置面板")
                },
            )
        }

        composeRule.onNodeWithText("统一设置面板").assertIsDisplayed()
        composeRule.onNodeWithText("目录").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("朗读").assertCountEquals(1)
    }

    @Test
    fun immersiveRowUsesSameResumeLabelModel() {
        var stopClicks = 0

        composeRule.setContent {
            ReaderControls(
                chromeMode = ReaderChromeMode.READING_ONLY,
                appearanceMode = ReaderAppearanceMode.DAY,
                progressSummary = "1/1",
                showChapterNavigationRow = false,
                canOpenPreviousChapter = false,
                canOpenNextChapter = false,
                themePalette = ReaderThemePalette(
                    background = androidx.compose.ui.graphics.Color.White,
                    surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                    content = androidx.compose.ui.graphics.Color.Black,
                ),
                ttsToggleState = ReaderTtsToggleUiState(
                    actionLabel = "继续朗读 · 28m",
                    showImmersiveAction = true,
                    immersiveActionLabel = "继续朗读 · 28m",
                ),
                onOpenPreviousChapter = {},
                onOpenNextChapter = {},
                onOpenToc = {},
                onToggleAppearanceMode = {},
                onOpenSettings = {},
                onImmersiveTtsAction = { stopClicks += 1 },
            )
        }

        composeRule.onNodeWithText("继续朗读 · 28m").assertIsDisplayed().performClick()
        assertEquals(1, stopClicks)
    }
}
