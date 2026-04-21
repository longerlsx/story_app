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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderContinuousScrollTest {

    @Test
    fun scrollModeCanReachNextChapterWithoutExplicitChapterButtons() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-continuous-scroll.txt").apply {
            writeText(
                """
                《连续滚动测试》
                作者：测试作者

                第1章 开始
                第一章内容第一段。
                第一章内容第二段。
                第一章内容第三段。
                第一章内容第四段。
                第一章内容第五段。
                第一章内容第六段。
                第一章内容第七段。
                第一章内容第八段。
                第一章内容第九段。
                第一章内容第十段。

                第2章 继续
                第二章独有内容。
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
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章内容第一段")), 8_000))
            assertTrue(device.ensureScrollMode())
            assertTrue(device.revealReaderChrome("设置"))
            assertFalse(device.hasObject(By.text("上一章")))
            assertFalse(device.hasObject(By.text("下一章")))
            assertTrue(device.scrollUntilTextVisible("第二章独有内容", attempts = 16))
        }
    }
}
