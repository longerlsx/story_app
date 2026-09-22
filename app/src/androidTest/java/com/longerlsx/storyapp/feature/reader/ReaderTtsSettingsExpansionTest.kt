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
    fun readingSettingsRemainIndependentWhileListeningCanOpenDirectly() {
        ActivityScenario.launch<MainActivity>(buildImportIntent("reader-listening-independent")).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.revealReaderChrome("设置"))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))
            assertTrue(device.wait(Until.hasObject(By.text("亮度")), 3_000))
            assertFalse(device.hasObject(By.descContains("设置分页：")))
            assertFalse(device.hasObject(By.text("定时关闭")))

            val read = device.wait(Until.findObject(By.desc("朗读")), 3_000)
            assertTrue(read != null && device.clickObjectCenter(read))
            assertTrue(device.wait(Until.hasObject(By.text("定时关闭")), 3_000))
            assertTrue(device.wait(Until.hasObject(By.desc("暂停朗读")), 3_000))
            assertTrue(device.hasObject(By.text("雷军 · 合成音色")))
            assertFalse(device.hasObject(By.text("亮度")))
            assertFalse(device.hasObject(By.text("从当前文字开始")))
            assertFalse(device.hasObject(By.text("从章节开头开始")))
        }
    }

    @Test
    fun collapseAndOpenListeningPanelPreservePlaybackAndExposeReadingNavigation() {
        ActivityScenario.launch<MainActivity>(buildImportIntent("reader-listening-collapse")).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.revealReaderChrome("朗读"))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.TTS))
            assertTrue(device.wait(Until.hasObject(By.desc("暂停朗读")), 3_000))
            val collapse = device.wait(Until.findObject(By.desc("收起听书面板")), 3_000)
            assertTrue(collapse != null && device.clickObjectCenter(collapse))
            assertTrue(device.wait(Until.gone(By.text("定时关闭")), 3_000))
            assertTrue(device.hasObject(By.desc("暂停朗读")))
            assertTrue(device.hasObject(By.desc("停止朗读")))

            val information = device.wait(Until.findObject(By.desc("展开听书面板")), 3_000)
            assertTrue(information != null && device.clickObjectCenter(information))
            assertTrue(device.wait(Until.hasObject(By.text("定时关闭")), 3_000))
            assertTrue(device.hasObject(By.desc("暂停朗读")))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.DIRECTORY))
            assertTrue(device.wait(Until.hasObject(By.text("第1章 开始")), 3_000))
            assertFalse(device.hasObject(By.text("定时关闭")))
        }
    }

    @Test
    fun longPressOpensListeningSettingsWithoutStartingOnRelease() {
        ActivityScenario.launch<MainActivity>(buildImportIntent("reader-listening-long-press")).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.revealReaderChrome("朗读"))
            assertTrue(device.longPressActionLabel("朗读"))
            assertTrue(device.wait(Until.hasObject(By.text("定时关闭")), 3_000))
            assertTrue(device.hasObject(By.desc("开始朗读")))
            assertFalse(device.hasObject(By.desc("暂停朗读")))
            assertFalse(device.hasObject(By.descContains("设置分页：")))
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
        return Result.success(listOf(ReaderTtsVoiceOption("test-voice", "测试语音")))
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
