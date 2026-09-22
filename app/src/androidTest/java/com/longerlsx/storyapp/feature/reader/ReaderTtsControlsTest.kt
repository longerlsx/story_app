package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderTtsControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun continuationTextTouchDoesNotTriggerTheImmersiveBackButton() {
        var backClicks = 0
        var textClicks = 0
        composeRule.setContent {
            Box(Modifier.width(320.dp).height(200.dp).testTag("continuation-touch")) {
                Box(Modifier.fillMaxWidth().padding(top = 52.dp).height(148.dp)
                    .clickable { textClicks++ }) { Text("续页第一行正文") }
                ReaderImmersiveHeader("第二页", ReaderThemePreset.PAPER.palette(), { backClicks++ })
            }
        }
        composeRule.onNodeWithTag("continuation-touch").performTouchInput {
            click(Offset(20.dp.toPx(), 55.dp.toPx()))
        }
        assertEquals("正文首行左端必须留给阅读手势", 0, backClicks)
        assertEquals(1, textClicks)
    }

    @Test
    fun visibleControlsSeparateOpeningListeningFromPlaybackAndStop() =
        assertIndependentListeningActions(ReaderChromeMode.CHROME_VISIBLE)

    @Test
    fun immersiveControlsSeparateOpeningListeningFromPlaybackAndStop() =
        assertIndependentListeningActions(ReaderChromeMode.READING_ONLY)

    private fun assertIndependentListeningActions(mode: ReaderChromeMode) {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            ReaderControls(
                chromeMode = mode, appearanceMode = ReaderAppearanceMode.DAY,
                progressSummary = "1/2", showChapterNavigationRow = false,
                canOpenPreviousChapter = false, canOpenNextChapter = true,
                themePalette = ReaderThemePreset.PAPER.palette(),
                ttsToggleState = ReaderTtsToggleUiState("听书", true, "继续朗读",
                    statusText = "已暂停", remainingTimeLabel = "28m", speechRate = 1.45f),
                onOpenPreviousChapter = {}, onOpenNextChapter = {}, onOpenToc = {},
                onToggleAppearanceMode = {}, onOpenSettings = {},
                onToggleTts = { actions += "toolbar" },
                onOpenListening = { actions += "open" },
                onImmersiveTtsAction = { actions += "resume" },
                onStopTts = { actions += "stop" },
            )
        }
        composeRule.onNodeWithContentDescription("展开听书面板").performClick()
        composeRule.onNodeWithContentDescription("继续朗读").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("停止朗读").assertIsDisplayed().performClick()
        assertEquals(listOf("open", "resume", "stop"), actions)
        composeRule.onNodeWithText("剩余 28m · 1.45×").assertIsDisplayed()
        composeRule.onAllNodesWithText("继续朗读").assertCountEquals(0)
        composeRule.onAllNodesWithText("停止朗读").assertCountEquals(0)
        if (mode == ReaderChromeMode.CHROME_VISIBLE) {
            composeRule.onNodeWithContentDescription("听书").performClick()
            assertEquals("toolbar", actions.last())
        }
    }

    @Test
    fun longPressListeningOpensSettingsWithoutAlsoStartingOnRelease() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            ReaderControls(
                chromeMode = ReaderChromeMode.CHROME_VISIBLE, appearanceMode = ReaderAppearanceMode.DAY,
                progressSummary = "1/1", showChapterNavigationRow = false,
                canOpenPreviousChapter = false, canOpenNextChapter = false,
                themePalette = ReaderThemePreset.PAPER.palette(), ttsToggleState = ReaderTtsToggleUiState(),
                onOpenPreviousChapter = {}, onOpenNextChapter = {}, onOpenToc = {},
                onToggleAppearanceMode = {}, onOpenSettings = {},
                onToggleTts = { actions += "start" }, onOpenListeningSettings = { actions += "settings" },
            )
        }
        composeRule.onNodeWithContentDescription("朗读").performTouchInput { longClick() }
        assertEquals(listOf("settings"), actions)
        composeRule.onNodeWithContentDescription("朗读").performClick()
        assertEquals(listOf("settings", "start"), actions)
    }

    @Test
    fun listeningExpandedKeepsOriginalReadingNavigationAndDoesNotDuplicatePlaybackActions() {
        composeRule.setContent {
            ReaderControls(
                chromeMode = ReaderChromeMode.LISTENING_EXPANDED,
                appearanceMode = ReaderAppearanceMode.DAY, progressSummary = "1/1",
                showChapterNavigationRow = false, canOpenPreviousChapter = false, canOpenNextChapter = false,
                themePalette = ReaderThemePreset.PAPER.palette(),
                ttsToggleState = ReaderTtsToggleUiState("听书", true, "暂停朗读", statusText = "正在朗读"),
                onOpenPreviousChapter = {}, onOpenNextChapter = {}, onOpenToc = {},
                onToggleAppearanceMode = {}, onOpenSettings = {},
                expandedContent = {
                    ReaderTtsSettingsSheet(
                        statusText = "正在朗读", settings = com.longerlsx.storyapp.core.model.ReaderTtsSettings(),
                        themePalette = ReaderThemePreset.PAPER.palette(), primaryActionLabel = "暂停朗读",
                        onPrimaryAction = {}, onStop = {}, onClose = {},
                        onUpdateSpeechRate = {}, onUpdatePitch = {}, onUpdateTimerPreset = {},
                    )
                },
            )
        }
        composeRule.onNodeWithText("定时关闭").assertIsDisplayed()
        composeRule.onNodeWithText("目录").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("暂停朗读").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("停止朗读").assertCountEquals(1)
    }

    @Test
    fun chapterProgressSummaryStaysSingleLineBetweenNavigationActions() {
        val progressSummary = "很长很长的前言标题会挤压章节导航栏"

        composeRule.setContent {
            Box(modifier = Modifier.width(320.dp)) {
                ReaderControls(
                    chromeMode = ReaderChromeMode.CHROME_VISIBLE,
                    appearanceMode = ReaderAppearanceMode.DAY,
                    progressSummary = progressSummary,
                    showChapterNavigationRow = true,
                    canOpenPreviousChapter = true,
                    canOpenNextChapter = true,
                    themePalette = ReaderThemePalette(
                        background = androidx.compose.ui.graphics.Color.White,
                        surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                        content = androidx.compose.ui.graphics.Color.Black,
                    ),
                    ttsToggleState = null,
                    onOpenPreviousChapter = {},
                    onOpenNextChapter = {},
                    onOpenToc = {},
                    onToggleAppearanceMode = {},
                    onOpenSettings = {},
                )
            }
        }

        val progressBounds = composeRule
            .onNodeWithText(progressSummary)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val progressHeight = progressBounds.bottom - progressBounds.top
        val actionBounds = composeRule
            .onNodeWithText("上一章")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val actionHeight = actionBounds.bottom - actionBounds.top

        assertTrue(
            "Chapter progress summary should stay single-line between navigation actions; actual height=$progressHeight, reference single-line height=$actionHeight",
            progressHeight <= actionHeight,
        )
    }

    @Test
    fun disabledChapterNavigationActionsExposeDisabledSemantics() {
        composeRule.setContent {
            ReaderControls(
                chromeMode = ReaderChromeMode.CHROME_VISIBLE,
                appearanceMode = ReaderAppearanceMode.DAY,
                progressSummary = "1/2章",
                showChapterNavigationRow = true,
                canOpenPreviousChapter = false,
                canOpenNextChapter = true,
                themePalette = ReaderThemePalette(
                    background = androidx.compose.ui.graphics.Color.White,
                    surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                    content = androidx.compose.ui.graphics.Color.Black,
                ),
                ttsToggleState = null,
                onOpenPreviousChapter = {},
                onOpenNextChapter = {},
                onOpenToc = {},
                onToggleAppearanceMode = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithContentDescription("上一章").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("下一章").assertIsEnabled()
    }
}
