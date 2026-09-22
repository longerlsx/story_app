package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderTtsSettingsSheetTest {
    @get:Rule val composeRule = createComposeRule()
    private val palette = ReaderThemePreset.PAPER.palette()

    @Test
    fun listeningPanelExposesIconPlaybackControlsAndKeepsCommonSettingsAheadOfVoice() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ReaderTtsSettingsSheet(
                    statusText = "正在朗读", settings = ReaderTtsSettings(), themePalette = palette,
                    primaryActionLabel = "暂停朗读", onPrimaryAction = { actions += "pause" },
                    onStop = { actions += "stop" }, onClose = { actions += "collapse" },
                    onUpdateSpeechRate = {}, onUpdatePitch = {}, onUpdateTimerPreset = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("暂停朗读").performClick()
        composeRule.onNodeWithContentDescription("停止朗读").performClick()
        composeRule.onNodeWithContentDescription("收起听书面板").performClick()
        assertEquals(listOf("pause", "stop", "collapse"), actions)
        composeRule.onAllNodesWithText("暂停朗读").assertCountEquals(0)
        composeRule.onAllNodesWithText("停止朗读").assertCountEquals(0)
        val timer = composeRule.onNodeWithText("定时关闭").getUnclippedBoundsInRoot()
        val speed = composeRule.onNodeWithText("语速").getUnclippedBoundsInRoot()
        val voice = composeRule.onNodeWithText("音色").getUnclippedBoundsInRoot()
        assertTrue(timer.top < speed.top && speed.top < voice.top)
    }

    @Test
    fun timerMenuKeepsLongDurationsOutOfTheCommonControlsAndSelectsOnce() {
        val timers = mutableListOf<ReaderTtsTimerPreset>()
        composeRule.setContent {
            MaterialTheme {
                ReaderTtsSettingsSheet(
                    statusText = "已暂停", settings = ReaderTtsSettings(), themePalette = palette,
                    primaryActionLabel = "继续朗读", onPrimaryAction = {}, onStop = {}, onClose = {},
                    onUpdateSpeechRate = {}, onUpdatePitch = {}, onUpdateTimerPreset = { timers += it },
                )
            }
        }
        composeRule.onAllNodesWithText("90 分钟").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("更多定时时长").performClick()
        composeRule.onNodeWithText("90 分钟").performClick()
        assertEquals(listOf(ReaderTtsTimerPreset.Countdown(90)), timers)
        composeRule.onAllNodesWithText("90 分钟").assertCountEquals(0)
    }

    @Test
    fun choosingAlreadySelectedTimerDoesNotResetTheCountdown() {
        val timers = mutableListOf<ReaderTtsTimerPreset>()
        composeRule.setContent {
            var settings by remember { mutableStateOf(ReaderTtsSettings(timerPreset = ReaderTtsTimerPreset.Countdown(30))) }
            MaterialTheme {
                ReaderTtsSettingsSheet(
                    statusText = "已暂停", settings = settings, themePalette = palette,
                    primaryActionLabel = "继续朗读", onPrimaryAction = {}, onStop = {}, onClose = {},
                    onUpdateSpeechRate = {}, onUpdatePitch = {},
                    onUpdateTimerPreset = { timers += it; settings = settings.copy(timerPreset = it) },
                )
            }
        }
        composeRule.onNodeWithContentDescription("30 分钟").performClick()
        composeRule.onNodeWithContentDescription("更多定时时长").performClick()
        composeRule.onNodeWithText("120 分钟").performClick()
        composeRule.onNodeWithContentDescription("更多定时时长").performClick()
        // The same value is visible in the heading and in the menu; the menu action owns click semantics.
        composeRule.onNode(androidx.compose.ui.test.hasText("120 分钟") and androidx.compose.ui.test.hasClickAction()).performClick()
        assertEquals(listOf(ReaderTtsTimerPreset.Countdown(120)), timers)
    }

    @Test
    fun exactSpeechRateChoicesForwardTheDisplayedValueAndPitchCanBeExpanded() {
        val rates = mutableListOf<Float>()
        val pitches = mutableListOf<Float>()
        composeRule.setContent {
            MaterialTheme {
                ReaderTtsSettingsSheet(
                    statusText = "未开始", settings = ReaderTtsSettings(), themePalette = palette,
                    primaryActionLabel = "开始朗读", onPrimaryAction = {}, onStop = null, onClose = {},
                    onUpdateSpeechRate = { rates += it }, onUpdatePitch = { pitches += it }, onUpdateTimerPreset = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("0.85×").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("1.45×").performClick()
        composeRule.onNodeWithContentDescription("2×").performClick()
        assertEquals(listOf(0.85f, 1.45f, 2f), rates)
        composeRule.onAllNodesWithText("0.8").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("展开更多选项").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("0.8").performScrollTo().performClick()
        assertEquals(listOf(0.8f), pitches)
    }

    @Test
    fun stopAndSettingChangesDoNotMovePanelOrCollapseTheOpenMoreSection() {
        composeRule.setContent {
            var stopped by remember { mutableStateOf(false) }
            var settings by remember { mutableStateOf(ReaderTtsSettings()) }
            MaterialTheme {
                ReaderTtsSettingsSheet(
                    statusText = if (stopped) "未开始" else "正在朗读", settings = settings, themePalette = palette,
                    primaryActionLabel = if (stopped) "开始朗读" else "暂停朗读", onPrimaryAction = {},
                    onStop = if (stopped) null else ({ stopped = true }), onClose = {},
                    onUpdateSpeechRate = { settings = settings.copy(speechRate = it) }, onUpdatePitch = {},
                    onUpdateTimerPreset = { settings = settings.copy(timerPreset = it) },
                )
            }
        }
        composeRule.onNodeWithContentDescription("展开更多选项").performScrollTo().performClick()
        val before = composeRule.onNodeWithContentDescription("听书面板").getUnclippedBoundsInRoot()
        composeRule.onNodeWithContentDescription("2×").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("停止朗读").performClick()
        val after = composeRule.onNodeWithContentDescription("听书面板").getUnclippedBoundsInRoot()
        assertEquals(before, after)
        composeRule.onNodeWithContentDescription("收起更多选项").assertExists()
        composeRule.onAllNodesWithText("已保存", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("已停止；", substring = true).assertCountEquals(0)
    }

    @Test
    fun shortViewportKeepsPlaybackAndStopReachableWhileSettingsScroll() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).height(240.dp)) {
                    ReaderTtsSettingsSheet(
                        statusText = "正在朗读", settings = ReaderTtsSettings(), themePalette = palette,
                        primaryActionLabel = "暂停朗读", onPrimaryAction = { actions += "pause" },
                        onStop = { actions += "stop" }, onClose = {},
                        onUpdateSpeechRate = {}, onUpdatePitch = {}, onUpdateTimerPreset = {},
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("展开更多选项").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("0.8").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("暂停朗读").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("停止朗读").assertIsDisplayed().performClick()
        assertEquals(listOf("pause", "stop"), actions)
    }

    @Test
    fun failureKeepsItsFullReasonAndOffersRetryInTheFixedHeader() {
        var retries = 0
        composeRule.setContent {
            MaterialTheme {
                ReaderTtsSettingsSheet(
                    statusText = "朗读失败", settings = ReaderTtsSettings(), themePalette = palette,
                    primaryActionLabel = "重试朗读", onPrimaryAction = { retries++ }, onStop = {}, onClose = {},
                    onUpdateSpeechRate = {}, onUpdatePitch = {}, onUpdateTimerPreset = {},
                    errorText = "声音准备失败，请重试。",
                )
            }
        }
        composeRule.onNodeWithText("声音准备失败，请重试。").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("重试朗读").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun listeningChoicesUseReaderPaletteInsteadOfMaterialPrimary() {
        val trap = Color(0xFFFF00FF)
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = trap)) {
                ReaderTtsSettingsSheet(
                    statusText = "未开始", settings = ReaderTtsSettings(), themePalette = ReaderThemePreset.CHARCOAL.palette(),
                    primaryActionLabel = "开始朗读", onPrimaryAction = {}, onStop = null, onClose = {},
                    onUpdateSpeechRate = {}, onUpdatePitch = {}, onUpdateTimerPreset = {},
                )
            }
        }
        assertFalse(composeRule.onNodeWithContentDescription("1×").captureToImage().toPixelMap().containsTrapColor(trap))
    }

    @Test
    fun readingSettingsRemainIndependentAndThemeSwatchesUseReaderPalette() {
        val trap = Color(0xFFFF00FF)
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(onSurface = trap, outline = trap)) {
                ReaderSettingsSheet(ReaderSettings(dayThemePreset = ReaderThemePreset.PAPER), palette, {})
            }
        }
        composeRule.onNodeWithText("亮度").assertIsDisplayed()
        composeRule.onAllNodesWithText("护眼模式").assertCountEquals(0)
        composeRule.onAllNodesWithText("朗读").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("切换主题：暖黄").performScrollTo()
        assertFalse(composeRule.onNodeWithContentDescription("当前主题：纸白").captureToImage().toPixelMap().containsTrapColor(trap))
        assertFalse(composeRule.onNodeWithContentDescription("切换主题：暖黄").captureToImage().toPixelMap().containsTrapColor(trap))
    }
}
