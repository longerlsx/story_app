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
class ReaderSettingsPersistenceTest {

    @Test
    fun changedReaderSettingsPersistAcrossRelaunch() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-settings-persist.txt").apply {
            writeText(
                """
                《设置持久化测试》
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

            val pageMode = device.wait(Until.findObject(By.text("翻页")), 3_000)
            assertNotNull(pageMode)
            assertTrue(device.clickObjectCenter(pageMode!!))

            val fontUp = device.wait(Until.findObject(By.text("A+")), 3_000)
            assertNotNull(fontUp)
            assertTrue(device.clickObjectCenter(fontUp!!))
        }

        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        ).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.revealReaderChrome("设置"))
            assertTrue(device.openReaderSettings())
            assertTrue(device.wait(Until.hasObject(By.desc("字号：20")), 8_000))
            assertTrue(device.wait(Until.hasObject(By.desc("阅读模式：翻页，已选中")), 8_000))
        }
    }
}
