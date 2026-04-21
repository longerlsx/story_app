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
class ReaderPageModeTest {

    @Test
    fun pagingBackwardAcrossBoundaryOpensPreviousChapterNearItsEnd() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-page-boundary.txt").apply {
            writeText(
                """
                《翻页边界测试》
                第1章 开始
                第一章铺垫一，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫四，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫五，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫六，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫七，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫八，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫九，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十一，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十二，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十三，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十四，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十五，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十六，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十七，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十八，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫十九，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十一，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十二，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十三，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十四，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十五，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十六，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十七，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十八，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫二十九，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十一，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十二，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十三，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十四，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十五，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十六，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十七，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十八，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫三十九，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章铺垫四十，这是一段明显偏长、用来撑开翻页内容的正文描述。
                第一章结尾标记。

                第2章 继续
                第二章开头内容。
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
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章铺垫一")), 8_000))
            assertTrue(device.ensurePageMode())
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

            assertTrue(device.wait(Until.hasObject(By.textContains("第二章开头内容")), 8_000))

            device.click((device.displayWidth * 0.12f).toInt(), device.displayHeight / 2)
            device.waitForIdle()

            assertTrue(device.wait(Until.hasObject(By.textContains("第一章结尾标记")), 8_000))
        }
    }

    @Test
    fun pageModeKeepsImmersiveChromeAndRestoresLastChapter() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-page-mode.txt").apply {
            writeText(
                """
                《翻页模式测试》
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
            assertFalse(device.hasObject(By.text("设置")))
            assertTrue(device.hasObject(By.desc("沉浸式阅读头部")))
            assertFalse(device.hasObject(By.desc("阅读器顶部栏")))

            assertTrue(device.ensurePageMode())

            assertTrue(device.revealReaderChrome("下一章"))
            assertTrue(device.tapChapterAction(previous = false))

            assertTrue(device.wait(Until.hasObject(By.textContains("第二章正文")), 8_000))
        }

        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        ).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第二章正文")), 8_000))
            assertFalse(device.hasObject(By.text("设置")))

            assertTrue(device.revealReaderChrome("设置"))
            assertTrue(device.openReaderSettings())
            assertTrue(device.wait(Until.hasObject(By.desc("阅读模式：翻页，已选中")), 8_000))
        }
    }
}
