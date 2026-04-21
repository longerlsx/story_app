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
class ReaderAppearanceToggleTest {

    @Test
    fun directAppearanceToggleRemembersSeparateDayAndNightThemes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-appearance-toggle.txt").apply {
            writeText(
                """
                《日夜切换测试》
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
            assertTrue(device.openReaderSettings())

            val dayTheme = device.wait(Until.findObject(By.desc("切换主题：暖黄")), 3_000)
            assertNotNull(dayTheme)
            dayTheme!!.click()
            assertTrue(device.wait(Until.hasObject(By.desc("当前主题：暖黄")), 3_000))

            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.APPEARANCE))

            assertTrue(device.wait(Until.hasObject(By.text("日间")), 3_000))
            val nightTheme = device.wait(Until.findObject(By.desc("切换主题：深灰")), 3_000)
            assertNotNull(nightTheme)
            nightTheme!!.click()
            assertTrue(device.wait(Until.hasObject(By.desc("当前主题：深灰")), 3_000))

            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.APPEARANCE))
            assertTrue(device.wait(Until.hasObject(By.desc("当前主题：暖黄")), 3_000))

            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.APPEARANCE))
            assertTrue(device.wait(Until.hasObject(By.desc("当前主题：深灰")), 3_000))
        }
    }
}
