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
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingMode
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChromeTest {

    @Before
    fun setUp() {
        resetStoryAppState(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        resetStoryAppState(ApplicationProvider.getApplicationContext())
    }

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
    fun scrollModeShellTapOnBodyTextRevealsReaderChrome() {
        val application = ApplicationProvider.getApplicationContext<StoryApplication>()
        application.readerSettingsStore.save(ReaderSettings(readingMode = ReadingMode.SCROLL))
        val importFile = File(application.cacheDir, "reader-scroll-body-shell-tap.txt").apply {
            writeText(
                """
                《滚动正文点击测试》
                作者：测试作者

                第1章 开始
                顶部铺垫正文，用于确认初始沉浸阅读态。
                第二段铺垫正文，用于把后续正文推到屏幕中部。
                滚动中心命中正文标记，这一段需要足够长，让 Text 的可见边界横跨屏幕中心；点击正文文字本身时应打开阅读器顶部栏和底部操作栏，而不是只停留在沉浸式阅读头部。继续补充真实小说式长句，确保中间区域是正文文本本身，不是段落间空白或容器背景。
                底部补充正文，用于让当前屏保持接近真实小说的一屏正文密度。
                """.trimIndent(),
            )
        }
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            importFile,
        )
        val externalIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(application, MainActivity::class.java)
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        ActivityScenario.launch<MainActivity>(externalIntent).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("滚动中心命中正文标记")), 8_000))
            assertTrue(device.wait(Until.hasObject(By.desc("沉浸式阅读头部")), 3_000))
            assertFalse(device.hasObject(By.desc("阅读器顶部栏")))
            assertFalse(device.hasObject(By.text("目录")) || device.hasObject(By.desc("目录")))
            assertFalse(device.hasObject(By.text("设置")) || device.hasObject(By.desc("设置")))
            assertFalse(device.hasObject(By.text("朗读")) || device.hasObject(By.desc("朗读")))

            val targetText = device.wait(Until.findObject(By.textContains("滚动中心命中正文标记")), 3_000)
            assertNotNull(targetText)
            val targetBounds = targetText!!.visibleBounds
            val centerX = device.displayWidth / 2
            assertTrue(targetBounds.left < centerX)
            assertTrue(targetBounds.right > centerX)

            device.shellTap(centerX, targetBounds.centerY())

            assertTrue(
                "滚动模式正文中心点击后应显示阅读器顶部栏",
                device.wait(Until.hasObject(By.desc("阅读器顶部栏")), 2_000),
            )
            assertTrue(
                "滚动模式正文中心点击后应显示底部操作栏",
                device.wait(Until.hasObject(By.text("设置")), 1_000) ||
                    device.wait(Until.hasObject(By.desc("设置")), 1_000) ||
                    device.wait(Until.hasObject(By.text("朗读")), 1_000) ||
                    device.wait(Until.hasObject(By.desc("朗读")), 1_000),
            )
        }
    }

    @Test
    fun pageModeCenterTapRevealsTwoLayerReaderChromeFromReadingOnly() {
        val application = ApplicationProvider.getApplicationContext<StoryApplication>()
        application.readerSettingsStore.save(ReaderSettings(readingMode = ReadingMode.PAGE))
        val importFile = File(application.cacheDir, "reader-page-center-tap-chrome.txt").apply {
            writeText(
                """
                《翻页中心点击测试》
                作者：测试作者

                第1章 开始
                顶部铺垫正文，确认翻页模式初始沉浸阅读态。
                第二段铺垫正文，用于把后续正文推到屏幕中部。
                中心命中段落，点击这里时坐标仍处在翻页模式的中间区域，但落点是正文 TextView 而不是页面空白。这段内容需要足够长，模拟真实小说页面在屏幕中部被正文填满的状态，避免测试只覆盖空白区域点击。继续补充几句正文，让这一整个段落跨过屏幕中心线：会议结束以后，主角仍然坐在电脑前，反复确认需求、修改备注、整理材料，页面中部密密麻麻都是普通小说正文。再继续补充一些正常长度的句子，保证固定的中心点击不会落到段落间距、空白容器或页脚区域，而是落在真实文字所在的 TextView 内部。
                底部补充正文，用于让当前页保持接近真实小说的一屏正文密度。
                """.trimIndent(),
            )
        }
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            importFile,
        )
        val externalIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(application, MainActivity::class.java)
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        ActivityScenario.launch<MainActivity>(externalIntent).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("中心命中段落")), 8_000))
            assertTrue(device.wait(Until.hasObject(By.desc("沉浸式阅读头部")), 3_000))
            assertFalse(device.hasObject(By.desc("阅读器顶部栏")))
            assertFalse(device.hasObject(By.text("目录")) || device.hasObject(By.desc("目录")))
            assertFalse(device.hasObject(By.text("设置")) || device.hasObject(By.desc("设置")))
            assertFalse(device.hasObject(By.text("朗读")) || device.hasObject(By.desc("朗读")))

            val centerText = device.wait(Until.findObject(By.textContains("中心命中段落")), 3_000)
            assertNotNull(centerText)
            val centerTextBounds = centerText!!.visibleBounds
            val centerX = device.displayWidth / 2
            val centerY = device.displayHeight / 2
            assertTrue(centerTextBounds.left < centerX)
            assertTrue(centerTextBounds.right > centerX)
            assertTrue(centerTextBounds.top < centerY)
            assertTrue(centerTextBounds.bottom > centerY)

            device.click(centerX, centerY)
            device.waitForIdle()

            assertTrue(
                "页模式中心点击后应显示阅读器顶部栏",
                device.wait(Until.hasObject(By.desc("阅读器顶部栏")), 2_000),
            )
            assertTrue(
                "页模式中心点击后应显示底部操作栏",
                device.wait(Until.hasObject(By.text("目录")), 1_000) ||
                    device.wait(Until.hasObject(By.desc("目录")), 1_000) ||
                    device.wait(Until.hasObject(By.text("设置")), 1_000) ||
                    device.wait(Until.hasObject(By.desc("设置")), 1_000) ||
                    device.wait(Until.hasObject(By.text("朗读")), 1_000) ||
                    device.wait(Until.hasObject(By.desc("朗读")), 1_000),
            )
        }
    }

    @Test
    fun pageModeCenterTapOnLaterPageBodyTextRevealsReaderChrome() {
        val application = ApplicationProvider.getApplicationContext<StoryApplication>()
        application.readerSettingsStore.save(ReaderSettings(readingMode = ReadingMode.PAGE))
        val importFile = File(application.cacheDir, "reader-page-later-body-center-tap.txt").apply {
            writeText(
                buildString {
                    appendLine("《翻页后续页正文点击测试》")
                    appendLine("作者：测试作者")
                    appendLine()
                    appendLine("第1章 开始")
                    repeat(18) { index ->
                        appendLine("第一页铺垫正文${index + 1}，用于把目标段落推到后续页面，避免只验证首屏空白区域点击。")
                    }
                    appendLine(
                        "后续页中心命中正文标记，这一段需要足够长，让 TextView 的可见边界横跨屏幕中心；点击屏幕中心时应打开阅读器顶部栏和底部操作栏，而不是只停留在沉浸式阅读头部。继续补充真实小说式长句，确保中间区域是正文文本本身，不是段落间空白。",
                    )
                    appendLine("后续页尾部正文，保持页面有正常阅读密度。")
                },
            )
        }
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            importFile,
        )
        val externalIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(application, MainActivity::class.java)
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        ActivityScenario.launch<MainActivity>(externalIntent).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("第一页铺垫正文1")), 8_000))
            assertTrue(device.wait(Until.hasObject(By.desc("沉浸式阅读头部")), 3_000))

            val rightX = (device.displayWidth * 0.88f).toInt()
            val centerY = device.displayHeight / 2
            var targetText = device.wait(Until.findObject(By.textContains("后续页中心命中正文标记")), 500)
            repeat(10) {
                if (targetText == null) {
                    device.click(rightX, centerY)
                    device.waitForIdle()
                    targetText = device.wait(Until.findObject(By.textContains("后续页中心命中正文标记")), 500)
                }
            }
            assertNotNull(targetText)
            assertFalse(device.hasObject(By.desc("阅读器顶部栏")))

            val targetBounds = targetText!!.visibleBounds
            val centerX = device.displayWidth / 2
            assertTrue(targetBounds.left < centerX)
            assertTrue(targetBounds.right > centerX)

            device.shellTap(centerX, targetBounds.centerY())

            assertTrue(
                "后续页正文中心点击后应显示阅读器顶部栏",
                device.wait(Until.hasObject(By.desc("阅读器顶部栏")), 2_000),
            )
            assertTrue(
                "后续页正文中心点击后应显示底部操作栏",
                device.wait(Until.hasObject(By.text("设置")), 1_000) ||
                    device.wait(Until.hasObject(By.desc("设置")), 1_000) ||
                    device.wait(Until.hasObject(By.text("朗读")), 1_000) ||
                    device.wait(Until.hasObject(By.desc("朗读")), 1_000),
            )
        }
    }

    private fun UiDevice.shellTap(
        x: Int,
        y: Int,
    ) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input tap $x $y")
            .close()
        waitForIdle()
    }

    @Test
    fun pageModeProgressExcludesSyntheticPrefaceFromChapterCount() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "reader-chrome-preface-progress.txt").apply {
            writeText(
                """
                《前言进度测试》
                作者：测试作者

                这是一段导入后应保留为前言的文案。
                它不应该让第1章显示成第2章进度。

                第1章 乘船
                第一章正文。

                第2章 抵达
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

            assertTrue(device.wait(Until.hasObject(By.text("1/2章")), 3_000))
            assertFalse(device.hasObject(By.text("2/3章")))
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
            assertTrue(device.clickObjectCenter(chapterTwo!!))

            assertTrue(device.wait(Until.hasObject(By.textContains("第二章正文")), 8_000))
            device.waitForIdle()
            assertFalse(device.hasObject(By.text("设置")) || device.hasObject(By.desc("设置")))
            assertFalse(device.hasObject(By.text("上一章")) || device.hasObject(By.desc("上一章")))
        }
    }
}
