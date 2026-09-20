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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderTtsSettingsSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listeningActionsKeepCurrentStartResumeAndStopAsExplicitChoices() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            ReaderTtsSettingsSheet(
                statusText = "当前状态：已暂停",
                settings = ReaderTtsSettings(),
                themePalette = ReaderThemePalette(Color.White, Color.White, Color.Black),
                availableVoices = listOf(ReaderTtsVoiceOption("kokoro:59", "中文男声")),
                selectedVoiceName = "kokoro:59",
                onSelectVoice = {}, onUpdateSpeechRate = {}, onUpdatePitch = {}, onUpdateTimerPreset = {},
                onStartFromCurrent = { actions += "current" },
                onResumeSaved = { actions += "resume" },
                onStop = { actions += "stop" },
                onPreviewVoice = { actions += "preview" },
                savedListeningLabel = "第4章",
            )
        }
        composeRule.onNodeWithText("继续上次听书").performClick()
        composeRule.onNodeWithText("从当前文字开始").performClick()
        composeRule.onNodeWithText("停止朗读").performClick()
        composeRule.onNodeWithText("试听当前音色").performClick()
        assertEquals(listOf("resume", "current", "stop", "preview"), actions)
    }

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
    fun unavailableOfflineVoicesDoNotPromiseAnAutomaticSystemFallback() {
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
                    onSelectTab = {},
                    onUpdateSettings = {},
                    onUpdateTtsSettings = {},
                )
            }
        }

        composeRule.onAllNodesWithText("当前使用系统默认音色").assertCountEquals(0)
        composeRule.onNodeWithText("离线音色暂不可用，请重新打开听书设置。").assertIsDisplayed()
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

        composeRule.onNodeWithText("正在载入离线音色…").assertIsDisplayed()
        composeRule.onAllNodesWithText("当前使用系统默认音色").assertCountEquals(0)
        composeRule.onAllNodesWithText("离线音色暂不可用，请重新打开听书设置。").assertCountEquals(0)
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

        composeRule.onNodeWithText("声音在手机本地生成，无需联网。").assertIsDisplayed()
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

        org.junit.Assert.assertFalse(selectedVoice.containsTrapColor(materialPrimaryTrap))
        org.junit.Assert.assertFalse(selectedRate.containsTrapColor(materialPrimaryTrap))
    }

    @Test
    fun ttsVoiceChoiceWithLongSystemNameStaysSingleLine() {
        val longVoiceName = "中文（中国大陆）系统高质量自然女声音色增强版超长名称"

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(220.dp),
                ) {
                    ReaderSettingsSheet(
                        settings = ReaderSettings(
                            ttsSettings = ReaderTtsSettings(voiceName = "voice-long"),
                        ),
                        themePalette = ReaderThemePalette(
                            background = Color.White,
                            surface = Color(0xFFF4F4F4),
                            content = Color.Black,
                        ),
                        activeTab = ReaderSettingsTab.TTS,
                        availableVoices = listOf(
                            ReaderTtsVoiceOption(name = "voice-long", displayName = longVoiceName),
                            ReaderTtsVoiceOption(name = "voice-short", displayName = "系统女声"),
                        ),
                        selectedVoiceName = "voice-long",
                        ttsStatusText = "当前状态：未朗读",
                        onSelectTab = {},
                        onUpdateSettings = {},
                        onUpdateTtsSettings = {},
                    )
                }
            }
        }

        val longVoiceBounds = composeRule
            .onNodeWithText(longVoiceName)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val longVoiceHeight = longVoiceBounds.bottom - longVoiceBounds.top
        val referenceVoiceBounds = composeRule
            .onNodeWithText("系统女声")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val referenceVoiceHeight = referenceVoiceBounds.bottom - referenceVoiceBounds.top

        assertTrue(
            "TTS voice choice labels should stay single-line; actual height=$longVoiceHeight, reference single-line height=$referenceVoiceHeight",
            longVoiceHeight <= referenceVoiceHeight,
        )
    }

    @Test
    fun readingTabThemeSwatchBordersUseReaderPaletteInsteadOfMaterialSurfaceColors() {
        val materialOnSurfaceTrap = Color(0xFFFF00FF)
        val materialOutlineTrap = Color(0xFF00FFFF)

        composeRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    onSurface = materialOnSurfaceTrap,
                    outline = materialOutlineTrap,
                ),
            ) {
                ReaderSettingsSheet(
                    settings = ReaderSettings(
                        dayThemePreset = ReaderThemePreset.PAPER,
                    ),
                    themePalette = ReaderThemePalette(
                        background = Color(0xFF141820),
                        surface = Color(0xFF1D2330),
                        content = Color(0xFFE6EAF2),
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

        val selectedSwatch = composeRule
            .onNodeWithContentDescription("当前主题：纸白")
            .captureToImage()
            .toPixelMap()
        val unselectedSwatch = composeRule
            .onNodeWithContentDescription("切换主题：暖黄")
            .captureToImage()
            .toPixelMap()

        org.junit.Assert.assertFalse(selectedSwatch.containsTrapColor(materialOnSurfaceTrap))
        org.junit.Assert.assertFalse(unselectedSwatch.containsTrapColor(materialOutlineTrap))
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

    @Test
    fun settingsTabsStayVisibleWhenTtsSettingsBodyScrolls() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.height(180.dp)) {
                    ReaderSettingsSheet(
                        settings = ReaderSettings(),
                        themePalette = ReaderThemePalette(
                            background = Color.White,
                            surface = Color(0xFFF4F4F4),
                            content = Color.Black,
                        ),
                        activeTab = ReaderSettingsTab.TTS,
                        availableVoices = listOf(
                            ReaderTtsVoiceOption(name = "voice-a", displayName = "系统女声"),
                            ReaderTtsVoiceOption(name = "voice-b", displayName = "机械男声"),
                        ),
                        selectedVoiceName = "voice-a",
                        ttsStatusText = "当前状态：未朗读",
                        onSelectTab = {},
                        onUpdateSettings = {},
                        onUpdateTtsSettings = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("设置分页：阅读，未选中")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("120 分钟")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription("设置分页：阅读，未选中")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("设置分页：朗读，已选中")
            .assertIsDisplayed()
    }

    @Test
    fun switchingSettingsTabsStartsTheNewBodyAtTopAfterScrolling() {
        composeRule.setContent {
            MaterialTheme {
                var activeTab by remember { mutableStateOf(ReaderSettingsTab.TTS) }
                Box(modifier = Modifier.height(180.dp)) {
                    ReaderSettingsSheet(
                        settings = ReaderSettings(),
                        themePalette = ReaderThemePalette(
                            background = Color.White,
                            surface = Color(0xFFF4F4F4),
                            content = Color.Black,
                        ),
                        activeTab = activeTab,
                        availableVoices = listOf(
                            ReaderTtsVoiceOption(name = "voice-a", displayName = "系统女声"),
                            ReaderTtsVoiceOption(name = "voice-b", displayName = "机械男声"),
                        ),
                        selectedVoiceName = "voice-a",
                        ttsStatusText = "当前状态：未朗读",
                        onSelectTab = { activeTab = it },
                        onUpdateSettings = {},
                        onUpdateTtsSettings = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithText("120 分钟")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription("设置分页：阅读，未选中")
            .performClick()

        composeRule
            .onNodeWithContentDescription("亮度：80%")
            .assertIsDisplayed()
    }
}
