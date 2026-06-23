package com.longerlsx.storyapp.feature.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test
    fun ttsTabShowsVoiceLoadingStateBeforeVoiceCatalogHasLoaded() {
        composeRule.setContent {
            MaterialTheme {
                ReaderSettingsSheet(
                    settings = ReaderSettings(
                        ttsSettings = ReaderTtsSettings(voiceName = "cmn-cn-x-test-local"),
                    ),
                    themePalette = ReaderThemePalette(
                        background = androidx.compose.ui.graphics.Color.White,
                        surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                        content = androidx.compose.ui.graphics.Color.Black,
                    ),
                    activeTab = ReaderSettingsTab.TTS,
                    availableVoices = emptyList(),
                    availableVoicesLoaded = false,
                    selectedVoiceName = null,
                    ttsStatusText = "当前状态：未朗读",
                    ttsSystemDefaultVoiceStatus = "当前使用系统默认音色",
                    onSelectTab = {},
                    onUpdateSettings = {},
                    onUpdateTtsSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("正在加载系统音色...").assertIsDisplayed()
        composeRule.onAllNodesWithText("当前使用系统默认音色").assertCountEquals(0)
        composeRule.onAllNodesWithText("已筛除非大陆中文音色，当前将使用系统默认音色。").assertCountEquals(0)
    }

    @Test
    fun ttsTabDoesNotExposeDeveloperTerminologyInSupportingCopy() {
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
                    availableVoices = listOf(
                        ReaderTtsVoiceOption(name = "voice-a", displayName = "系统女声"),
                    ),
                    selectedVoiceName = null,
                    ttsStatusText = "当前状态：未朗读",
                    onSelectTab = {},
                    onUpdateSettings = {},
                    onUpdateTtsSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("仅显示可用的大陆中文音色。").assertIsDisplayed()
        composeRule.onAllNodesWithText("TTS engine", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("voice", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("短句块", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("机械感", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("新会话", substring = true).assertCountEquals(0)
    }

    @Test
    fun ttsTabSelectedChoicesUseReaderPaletteInsteadOfMaterialPrimary() {
        val materialPrimaryTrap = Color(0xFFFF00FF)

        composeRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = materialPrimaryTrap),
            ) {
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
                        background = Color(0xFF141820),
                        surface = Color(0xFF1D2330),
                        content = Color(0xFFE6EAF2),
                    ),
                    activeTab = ReaderSettingsTab.TTS,
                    availableVoices = listOf(
                        ReaderTtsVoiceOption(name = "voice-a", displayName = "系统女声"),
                        ReaderTtsVoiceOption(name = "voice-b", displayName = "机械男声"),
                    ),
                    selectedVoiceName = "voice-b",
                    ttsStatusText = "当前状态：未朗读",
                    onSelectTab = {},
                    onUpdateSettings = {},
                    onUpdateTtsSettings = {},
                )
            }
        }

        val selectedVoice = composeRule.onNodeWithText("机械男声").captureToImage().toPixelMap()
        val selectedRate = composeRule.onNodeWithText("1.0x").captureToImage().toPixelMap()

        org.junit.Assert.assertFalse(selectedVoice.containsMaterialPrimaryTrap())
        org.junit.Assert.assertFalse(selectedRate.containsMaterialPrimaryTrap())
    }

    @Test
    fun readingTabDoesNotRenderEyeCareLabelWithoutAControl() {
        composeRule.setContent {
            MaterialTheme {
                ReaderSettingsSheet(
                    settings = ReaderSettings(),
                    themePalette = ReaderThemePalette(
                        background = androidx.compose.ui.graphics.Color.White,
                        surface = androidx.compose.ui.graphics.Color(0xFFF4F4F4),
                        content = androidx.compose.ui.graphics.Color.Black,
                    ),
                    activeTab = ReaderSettingsTab.READING,
                    availableVoices = emptyList(),
                    selectedVoiceName = null,
                    ttsStatusText = "当前状态：未朗读",
                    onSelectTab = {},
                    onUpdateSettings = {},
                    onUpdateTtsSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("亮度").assertIsDisplayed()
        composeRule.onAllNodesWithText("护眼模式").assertCountEquals(0)
    }

    private fun androidx.compose.ui.graphics.PixelMap.containsMaterialPrimaryTrap(): Boolean {
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = this[x, y]
                if (
                    pixel.alpha > 0.2f &&
                    pixel.red > 0.75f &&
                    pixel.green < 0.25f &&
                    pixel.blue > 0.75f
                ) {
                    return true
                }
            }
        }
        return false
    }
}
