package com.longerlsx.storyapp.feature.reader

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.longerlsx.storyapp.MainActivity
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsEngine
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegment
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderTtsSettingsExpansionTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val application = context.applicationContext as StoryApplication
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() {
        resetStoryAppState(context)
        grantNotificationPermissionIfNeeded()
        application.setReaderTtsEngineFactoryForTests { callback ->
            VoiceListOnlyReaderTtsEngine(callback)
        }
    }

    @After
    fun tearDown() {
        application.setReaderTtsEngineFactoryForTests(null)
        resetStoryAppState(context)
    }

    @Test
    fun settingsOpensUnifiedTabsAndRemembersLastSelectedTab() {
        ActivityScenario.launch<MainActivity>(buildImportIntent("reader-tts-settings-expand")).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.revealReaderChrome("设置"))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))

            assertTrue(device.wait(Until.hasObject(By.text("阅读")), 3_000))
            assertTrue(device.wait(Until.hasObject(By.text("朗读")), 3_000))
            assertTrue(device.wait(Until.hasObject(By.text("亮度")), 3_000))
            assertFalse(device.hasObject(By.text("朗读设置")))
            assertFalse(device.hasObject(By.text("常规设置")))
            assertFalse(device.hasObject(By.text("关闭")))

            val ttsTab = device.wait(Until.findObject(By.text("朗读")), 3_000)
            assertTrue(ttsTab != null)
            assertTrue(device.clickObjectCenter(ttsTab!!))

            assertTrue(device.wait(Until.hasObject(By.text("测试语音")), 3_000))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))
            assertTrue(device.wait(Until.gone(By.text("测试语音")), 3_000))

            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))
            assertTrue(device.wait(Until.hasObject(By.text("测试语音")), 3_000))
        }
    }

    @Test
    fun firstOpenDefaultsToTtsTabWhenPlaybackIsActive() {
        ActivityScenario.launch<MainActivity>(buildImportIntent("reader-tts-settings-active-default")).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.revealReaderChrome("朗读"))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.TTS))
            assertTrue(device.wait(Until.hasObject(By.textContains("暂停朗读")), 3_000))

            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))
            assertTrue(device.wait(Until.hasObject(By.text("测试语音")), 3_000))
            assertFalse(device.hasObject(By.text("亮度")))
        }
    }

    @Test
    fun longTtsSettingsStillKeepsPrimaryActionBarUsable() {
        ActivityScenario.launch<MainActivity>(buildImportIntent("reader-tts-settings-bottom-bar")).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.revealReaderChrome("设置"))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))
            assertTrue(device.wait(Until.hasObject(By.text("阅读")), 3_000))

            val ttsTab = device.wait(Until.findObject(By.text("朗读")), 3_000)
            assertTrue(ttsTab != null)
            assertTrue(device.clickObjectCenter(ttsTab!!))

            assertTrue(device.wait(Until.hasObject(By.text("测试语音")), 3_000))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.DIRECTORY))
            assertTrue(device.wait(Until.hasObject(By.text("第1章 开始")), 3_000))
            assertFalse(device.hasObject(By.text("测试语音")))
        }
    }

    private fun buildImportIntent(fileName: String): Intent {
        val importFile = File(context.cacheDir, "$fileName.txt").apply {
            writeText(
                """
                《朗读设置展开测试》
                作者：测试作者

                第1章 开始
                第一章正文。
                """.trimIndent(),
            )
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            importFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun grantNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return
        }
        device.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        )
    }
}

private class VoiceListOnlyReaderTtsEngine(
    private val callback: ReaderTtsEngine.Callback,
) : ReaderTtsEngine {
    override suspend fun initialize(): Result<List<ReaderTtsVoiceOption>> {
        return Result.success(
            listOf(
                ReaderTtsVoiceOption(
                    name = "cmn-cn-x-test-local",
                    displayName = "测试语音",
                ),
                ReaderTtsVoiceOption(
                    name = "cmn-cn-x-voice-b-local",
                    displayName = "测试语音 B",
                ),
                ReaderTtsVoiceOption(
                    name = "cmn-cn-x-voice-c-local",
                    displayName = "测试语音 C",
                ),
                ReaderTtsVoiceOption(
                    name = "cmn-cn-x-voice-d-local",
                    displayName = "测试语音 D",
                ),
                ReaderTtsVoiceOption(
                    name = "cmn-cn-x-voice-e-local",
                    displayName = "测试语音 E",
                ),
                ReaderTtsVoiceOption(
                    name = "cmn-cn-x-voice-f-local",
                    displayName = "测试语音 F",
                ),
                ReaderTtsVoiceOption(
                    name = "cmn-cn-x-voice-g-local",
                    displayName = "测试语音 G",
                ),
                ReaderTtsVoiceOption(
                    name = "cmn-cn-x-voice-h-local",
                    displayName = "测试语音 H",
                ),
            ),
        )
    }

    override fun applySettings(settings: ReaderTtsSettings) = Unit

    override fun speak(
        utteranceId: String,
        segment: ReaderTtsSegment,
    ): Boolean {
        callback.onUtteranceStarted(utteranceId)
        return true
    }

    override fun stop() = Unit

    override fun shutdown() = Unit
}
