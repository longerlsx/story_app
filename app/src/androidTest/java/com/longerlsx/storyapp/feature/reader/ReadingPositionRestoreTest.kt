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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPositionRestoreTest {

    @Test
    fun relaunchRestoresApproximateScrollPosition() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val beforeMarker = "恢复位置前-110"
        val marker = "恢复位置标记-120"
        val afterMarker = "恢复位置后-130"
        val importFile = File(context.cacheDir, "reader-position-restore.txt").apply {
            writeText(
                buildString {
                    appendLine("《位置恢复测试》")
                    appendLine("作者：测试作者")
                    appendLine()
                    appendLine("第1章 长章")
                    for (index in 1..220) {
                        val line = when (index) {
                            110 -> beforeMarker
                            120 -> marker
                            130 -> afterMarker
                            else -> "正文段落-$index"
                        }
                        appendLine(line)
                    }
                },
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
            assertTrue(device.wait(Until.hasObject(By.textContains("正文段落-1")), 8_000))
            assertTrue(device.ensureScrollMode())
            assertTrue(device.scrollUntilTextVisible(marker))
        }

        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        ).use {
            assertTrue(
                device.wait(Until.hasObject(By.textContains(marker)), 3_000) ||
                    device.wait(Until.hasObject(By.textContains(beforeMarker)), 3_000) ||
                    device.wait(Until.hasObject(By.textContains(afterMarker)), 3_000),
            )
        }
    }
}
