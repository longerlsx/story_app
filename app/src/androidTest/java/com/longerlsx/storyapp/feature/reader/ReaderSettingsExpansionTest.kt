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
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsExpansionTest {

    @Test
    fun settingsExpansionReplacesChapterRowInPlace() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-settings-expand.txt").apply {
            writeText(
                """
                《设置展开测试》
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
        val externalIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        ActivityScenario.launch<MainActivity>(externalIntent).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.revealReaderChrome("设置"))
            assertTrue(device.hasObject(By.text("上一章")))

            val settingsButton = device.wait(Until.findObject(By.text("设置")), 3_000)
            assertNotNull(settingsButton)
            settingsButton!!.click()

            assertTrue(device.wait(Until.hasObject(By.text("亮度")), 3_000))
            assertTrue(device.hasObject(By.text("字号")))
            assertTrue(device.hasObject(By.text("段落距")))
            assertTrue(device.wait(Until.gone(By.text("上一章")), 3_000))
            assertTrue(device.hasObject(By.text("目录")))
            assertTrue(device.hasObject(By.text("设置")))
        }
    }

    @Test
    fun openingDirectoryFromSettingsClosesSettingsFirst() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-settings-directory-switch.txt").apply {
            writeText(
                """
                《设置目录切换测试》
                作者：测试作者

                第1章 开始
                第一章正文。

                第2章 继续
                第二章正文。
                """.trimIndent(),
            )
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            importFile,
        )
        val externalIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        ActivityScenario.launch<MainActivity>(externalIntent).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.openReaderSettings())

            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.DIRECTORY))

            assertTrue(device.wait(Until.hasObject(By.text("第2章 继续")), 3_000))
            assertTrue(device.wait(Until.gone(By.text("亮度")), 3_000))
        }
    }
}
