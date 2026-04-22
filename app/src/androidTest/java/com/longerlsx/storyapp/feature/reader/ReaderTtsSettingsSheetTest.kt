package com.longerlsx.storyapp.feature.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderTtsSettingsSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unifiedSheetRendersTabsAndForwardsTtsSettingUpdates() {
        val selectedTabs = mutableListOf<ReaderSettingsTab>()
        val updatedTtsSettings = mutableListOf<ReaderTtsSettings>()

        composeRule.setContent {
            MaterialTheme {
                ReaderSettingsSheet(
                    settings = ReaderSettings(
                        ttsSettings = ReaderTtsSettings(
                            voiceName = "voice-b",
                            speechRate = 1.0f,
                            pitch = 1.0f,
                            timerPreset = ReaderTtsTimerPreset.NoTimer,
                        ),
                    ),
                    themePalette = ReaderThemePalette(
                        background = androidx.compose.ui.graphics.Color.White,
                        surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                        content = androidx.compose.ui.graphics.Color.Black,
                    ),
                    activeTab = ReaderSettingsTab.TTS,
                    availableVoices = listOf(
                        ReaderTtsVoiceOption(name = "voice-a", displayName = "系统女声"),
                        ReaderTtsVoiceOption(name = "voice-b", displayName = "机械男声"),
                    ),
                    selectedVoiceName = "voice-b",
                    ttsStatusText = "当前状态：朗读中",
                    ttsRemainingTimeLabel = "28m",
                    onSelectTab = { selectedTabs += it },
                    onUpdateSettings = {},
                    onUpdateTtsSettings = { updatedTtsSettings += it },
                )
            }
        }

        composeRule.onNodeWithText("阅读").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("系统女声").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("30 分钟").assertIsDisplayed().performClick()

        assertEquals(listOf(ReaderSettingsTab.READING), selectedTabs)
        assertEquals("voice-a", updatedTtsSettings.first().voiceName)
        assertEquals(ReaderTtsTimerPreset.Countdown(30), updatedTtsSettings.last().timerPreset)
    }

    @Test
    fun ttsTabShowsSystemDefaultFallbackStatusWhenNoMainlandVoicesRemain() {
        composeRule.setContent {
            MaterialTheme {
                ReaderSettingsSheet(
                    settings = ReaderSettings(),
                    themePalette = ReaderThemePalette(
                        background = androidx.compose.ui.graphics.Color.White,
                        surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                        content = androidx.compose.ui.graphics.Color.Black,
                    ),
                    activeTab = ReaderSettingsTab.TTS,
                    availableVoices = emptyList(),
                    selectedVoiceName = null,
                    ttsStatusText = "当前状态：未朗读",
                    ttsSystemDefaultVoiceStatus = "当前使用系统默认音色",
                    onSelectTab = {},
                    onUpdateSettings = {},
                    onUpdateTtsSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("当前使用系统默认音色").assertIsDisplayed()
        composeRule.onNodeWithText("已筛除非大陆中文音色，当前将使用系统默认音色。").assertIsDisplayed()
    }
}
