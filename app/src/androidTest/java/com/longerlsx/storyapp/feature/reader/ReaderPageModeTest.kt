package com.longerlsx.storyapp.feature.reader

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.longerlsx.storyapp.MainActivity
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.data.book.FileAnchorStore
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPageModeTest {

    @Test
    fun pagingForwardAfterChapterSwitchWithDifferentPageCountOpensNextChapter() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        resetStoryAppState(context)
        val importFile = File(context.cacheDir, "reader-page-switch-forward-boundary.txt").apply {
            writeText(
                buildString {
                    appendLine("《翻页切章边界测试》")
                    appendLine("第1章 长章")
                    repeat(80) { index ->
                        appendLine("第一章长内容${index + 1}，用于让 pager 先停在非首页状态。")
                    }
                    appendLine()
                    appendLine("第2章 短章")
                    appendLine("第二章短章当前页标记。")
                    appendLine()
                    appendLine("第3章 目标")
                    appendLine("第三章边界开头标记。")
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
            assertTrue(device.wait(Until.hasObject(By.textContains("第一章长内容1")), 8_000))
            assertTrue(device.ensurePageMode())

            val rightX = (device.displayWidth * 0.88f).toInt()
            val centerY = device.displayHeight / 2
            repeat(3) {
                device.click(rightX, centerY)
                device.waitForIdle()
            }

            assertTrue(device.revealReaderChrome("下一章"))
            assertTrue(device.tapChapterAction(previous = false))
            assertTrue(device.wait(Until.hasObject(By.textContains("第二章短章当前页标记")), 8_000))
            device.waitForIdle()

            device.click(rightX, centerY)
            device.waitForIdle()

            assertTrue(device.wait(Until.hasObject(By.textContains("第三章边界开头标记")), 8_000))
        }
    }

    @Test
    fun pagingBackwardAcrossBoundaryOpensPreviousChapterNearItsEnd() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        resetStoryAppState(context)
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
            assertTrue(device.clickObjectCenter(chapterTwo!!))

            assertTrue(device.wait(Until.hasObject(By.textContains("第二章开头内容")), 8_000))

            device.click((device.displayWidth * 0.12f).toInt(), device.displayHeight / 2)
            device.waitForIdle()

            assertTrue(device.wait(Until.hasObject(By.textContains("第一章结尾标记")), 8_000))
        }
    }

    @Test
    fun pageModeKeepsImmersiveChromeAndRestoresLastChapter() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        resetStoryAppState(context)
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

/** Uses rendered Compose nodes: accessibility snapshots can retain the first page during paging. */
@RunWith(AndroidJUnit4::class)
class ReaderPageForwardInteractionTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun pagingForwardAcrossBoundaryByRightTapOpensNextChapter() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        resetStoryAppState(context)
        val importFile = File(context.cacheDir, "reader-page-forward-boundary.txt").apply {
            writeText(
                buildString {
                    appendLine("《翻页前进边界测试》")
                    appendLine("第1章 开始")
                    repeat(60) { index ->
                        appendLine("第一章铺垫${index + 1}，这是一段明显偏长、用来撑开翻页内容的正文描述。")
                    }
                    appendLine("第一章结尾标记。")
                    appendLine()
                    appendLine("第2章 继续")
                    appendLine("第二章边界开头标记。")
                    appendLine("第二章第二段内容。")
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
        val diskAnchors = FileAnchorStore(context.filesDir)
        fun awaitConfirmedPosition(previous: ReadingAnchor? = null): ReadingProgress {
            var confirmed: ReadingProgress? = null
            composeRule.waitUntil(timeoutMillis = 8_000) {
                confirmed = diskAnchors.loadAll().singleOrNull()?.takeIf { it.anchor != previous }
                confirmed != null
            }
            return checkNotNull(confirmed)
        }

        ActivityScenario.launch<MainActivity>(externalIntent).use {
            composeRule.waitUntil(timeoutMillis = 8_000) { hasVisibleText("第一章铺垫1") }
            org.junit.Assert.assertEquals(
                com.longerlsx.storyapp.core.model.ReadingMode.PAGE,
                com.longerlsx.storyapp.data.reader.ReaderSettingsStore(context.filesDir).load().readingMode,
            )
            awaitConfirmedPosition()

            var reachedFirstChapterEnd = hasVisibleText("第一章结尾标记")
            repeat(20) {
                if (!reachedFirstChapterEnd) {
                    val previous = checkNotNull(diskAnchors.loadAll().singleOrNull()).anchor
                    composeRule.onRoot().performTouchInput { click(Offset(width * 0.88f, center.y)) }
                    awaitConfirmedPosition(previous)
                    composeRule.waitForIdle()
                    reachedFirstChapterEnd = hasVisibleText("第一章结尾标记")
                }
            }
            assertTrue(reachedFirstChapterEnd)

            val previous = checkNotNull(diskAnchors.loadAll().singleOrNull()).anchor
            composeRule.onRoot().performTouchInput { click(Offset(width * 0.88f, center.y)) }
            awaitConfirmedPosition(previous)
            composeRule.waitForIdle()

            composeRule.waitUntil(timeoutMillis = 8_000) { hasVisibleText("第二章边界开头标记") }
        }
    }

    private fun hasVisibleText(text: String): Boolean {
        val viewport = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        return composeRule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).any { node ->
                node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f &&
                    node.positionInRoot.x >= viewport.left &&
                    node.positionInRoot.x + node.size.width <= viewport.right
            }
    }
}
