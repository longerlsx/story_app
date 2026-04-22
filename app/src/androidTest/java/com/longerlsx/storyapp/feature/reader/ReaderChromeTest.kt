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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChromeTest {

    @Test
    fun persistentHeaderShowsBackAndCurrentChapterWithoutRevealingChrome() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-persistent-header.txt").apply {
            writeText(
                """
                《常驻头部测试》
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
            assertTrue(device.wait(Until.hasObject(By.desc("沉浸式阅读头部")), 3_000))
            assertTrue(device.hasObject(By.desc("返回")))
            assertTrue(device.hasObject(By.descContains("沉浸式章节：第1章 开始")))
            assertFalse(device.hasObject(By.desc("阅读器顶部栏")))
            assertFalse(device.hasObject(By.desc("更多")))
            assertFalse(device.hasObject(By.text("设置")))
        }
    }

    @Test
    fun centerTapRevealsTwoLayerReaderChrome() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-chrome-layers.txt").apply {
            writeText(
                """
                《阅读器交互测试》
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
            assertTrue(device.ensureScrollMode())
            assertTrue(device.revealReaderChrome("设置"))

            assertTrue(device.waitForReaderTopBar("阅读器交互测试", timeoutMs = 4_000))
            assertTrue(device.hasObject(By.descContains("阅读器交互测试")))
            assertTrue(device.hasObject(By.descContains("第1章 开始")))
            assertTrue(device.hasObject(By.desc("更多")))
            assertFalse(device.hasObject(By.desc("沉浸式阅读头部")))
            assertTrue(
                device.wait(Until.hasObject(By.text("设置")), 2_000) ||
                    device.wait(Until.hasObject(By.desc("设置")), 2_000),
            )
            assertTrue(
                device.wait(Until.hasObject(By.text("夜间")), 2_000) ||
                    device.wait(Until.hasObject(By.text("日间")), 2_000) ||
                    device.wait(Until.hasObject(By.desc("夜间")), 2_000) ||
                    device.wait(Until.hasObject(By.desc("日间")), 2_000),
            )
            assertTrue(
                device.wait(Until.hasObject(By.text("朗读")), 2_000) ||
                    device.wait(Until.hasObject(By.desc("朗读")), 2_000),
            )
            assertFalse(device.hasObject(By.text("上一章")))
            assertFalse(device.hasObject(By.text("下一章")))
        }
    }

    @Test
    fun appearanceToggleResetsChromeAutoHideFromLastInteraction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-chrome-autohide-reset.txt").apply {
            writeText(
                """
                《操作层自动收起测试》
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
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.APPEARANCE))
            assertTrue(
                device.wait(Until.hasObject(By.text("设置")), 2_500) ||
                    device.wait(Until.hasObject(By.desc("设置")), 2_500),
            )
            assertTrue(
                device.wait(Until.gone(By.text("设置")), 2_000) ||
                    device.wait(Until.gone(By.desc("设置")), 2_000),
            )
        }
    }

    @Test
    fun closingSettingsRearmsChromeAutoHideCountdown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-chrome-settings-close-autohide.txt").apply {
            writeText(
                """
                《设置关闭后自动收起测试》
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
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))
            assertTrue(device.wait(Until.hasObject(By.text("亮度")), 3_000))

            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS))
            assertTrue(
                device.wait(Until.hasObject(By.text("目录")), 1_500) ||
                    device.wait(Until.hasObject(By.desc("目录")), 1_500),
            )
            assertTrue(
                device.wait(Until.gone(By.text("目录")), 4_000) ||
                    device.wait(Until.gone(By.desc("目录")), 4_000),
            )
        }
    }

    @Test
    fun chapterButtonsKeepChromeVisibleAfterNavigation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-button-nav-import.txt").apply {
            writeText(
                """
                《按钮翻章测试》
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
            assertTrue(device.ensurePageMode())

            assertTrue(device.revealReaderChrome("下一章"))
            assertTrue(device.tapChapterAction(previous = false))

            assertTrue(device.wait(Until.hasObject(By.textContains("第二章正文")), 8_000))
            assertTrue(device.hasObject(By.text("上一章")))
            assertTrue(device.hasObject(By.text("下一章")))

            assertTrue(device.revealReaderChrome("上一章"))
            assertTrue(device.tapChapterAction(previous = true))

            assertTrue(device.wait(Until.hasObject(By.textContains("第一章正文")), 8_000))
            assertTrue(device.hasObject(By.text("目录")))
        }
    }

    @Test
    fun directorySelectionReturnsToReadingOnly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-chrome-import.txt").apply {
            writeText(
                """
                《目录测试》
                作者：测试作者

                第1章 开始
                第一章正文。
                第一章补充一，这是一段专门用于拉长首章的正文内容。
                第一章补充二，这是一段专门用于拉长首章的正文内容。
                第一章补充三，这是一段专门用于拉长首章的正文内容。
                第一章补充四，这是一段专门用于拉长首章的正文内容。
                第一章补充五，这是一段专门用于拉长首章的正文内容。
                第一章补充六，这是一段专门用于拉长首章的正文内容。
                第一章补充七，这是一段专门用于拉长首章的正文内容。
                第一章补充八，这是一段专门用于拉长首章的正文内容。
                第一章补充九，这是一段专门用于拉长首章的正文内容。
                第一章补充十，这是一段专门用于拉长首章的正文内容。

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
            assertTrue(device.ensureScrollMode())

            assertTrue(device.revealReaderChrome("目录"))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.DIRECTORY))

            var chapterTwo = device.wait(Until.findObject(By.text("第2章 继续")), 3_000)
            if (chapterTwo == null) {
                assertTrue(device.revealReaderChrome("目录", attempts = 5))
                assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.DIRECTORY))
                chapterTwo = device.wait(Until.findObject(By.text("第2章 继续")), 5_000)
            }
            assertNotNull(chapterTwo)
            chapterTwo!!.click()

            assertTrue(device.wait(Until.hasObject(By.textContains("第二章正文")), 8_000))
            device.waitForIdle()
            assertFalse(device.hasObject(By.text("设置")) || device.hasObject(By.desc("设置")))
            assertFalse(device.hasObject(By.text("上一章")) || device.hasObject(By.desc("上一章")))
        }
    }
}
