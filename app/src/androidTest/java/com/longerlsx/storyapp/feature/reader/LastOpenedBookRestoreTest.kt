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
class LastOpenedBookRestoreTest {

    @Test
    fun plainLaunchRestoresMostRecentlyOpenedBook() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        resetStoryAppState(context)
        val uniqueTitle = "恢复测试-${System.currentTimeMillis()}"
        val uniqueBody = "恢复正文-${System.currentTimeMillis()}"
        val importFile = File(context.cacheDir, "restore-import.txt").apply {
            writeText(
                """
                《$uniqueTitle》
                作者：测试作者

                第1章 开始
                $uniqueBody
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
            assertTrue(device.wait(Until.hasObject(By.text(uniqueBody)), 8_000))
            assertTrue(device.wait(Until.hasObject(By.text("第1章 开始")), 8_000))
        }

        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }).use {
            assertTrue(device.wait(Until.hasObject(By.text(uniqueBody)), 8_000))
            assertTrue(device.wait(Until.hasObject(By.text("第1章 开始")), 8_000))
        }
    }
}
