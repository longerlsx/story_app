package com.longerlsx.storyapp.feature.reader

import android.content.Context
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
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.data.book.FileAnchorStore
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderNavigationInteractionTest {
    @Test
    fun swipeCrossesShortChapterAndReturnsToTheSameText() {
        withThreeChapters { device ->
            val y = device.displayHeight / 2
            // A quick fling must commit its destination, not the idle gap after pointer release.
            device.swipe(device.displayWidth * 8 / 10, y, device.displayWidth / 10, y, 6)
            assertTrue("滑动应进入第二章", device.wait(Until.hasObject(By.textContains("乙章唯一正文")), 5_000))
            assertConfirmedChapter(device, 1, "乙")
            device.swipe(device.displayWidth * 8 / 10, y, device.displayWidth / 10, y, 24)
            assertTrue("重建窗口后应继续进入第三章", device.wait(Until.hasObject(By.textContains("丙章唯一正文")), 5_000))
            assertConfirmedChapter(device, 2, "丙")
            device.swipe(device.displayWidth / 10, y, device.displayWidth * 8 / 10, y, 24)
            assertTrue(device.wait(Until.hasObject(By.textContains("乙章唯一正文")), 5_000))
            assertConfirmedChapter(device, 1, "乙")
            device.swipe(device.displayWidth / 10, y, device.displayWidth * 8 / 10, y, 24)
            assertTrue("反向应返回原页", device.wait(Until.hasObject(By.textContains("甲章唯一正文")), 5_000))
            assertConfirmedChapter(device, 0, "甲")
        }
    }

    private fun assertConfirmedChapter(device: UiDevice, chapter: Int, title: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val anchors = FileAnchorStore(context.filesDir)
        val chapterTitle = "第${chapter + 1}章 $title"
        val confirmed = runBlocking {
            withTimeoutOrNull(8_000) {
                while (true) {
                    // The fixture's first line is also its imported book title. Match complete
                    // production header identities; a body/preview Text node is not confirmation.
                    val headerMatches = device.hasObject(By.desc("沉浸式章节：$chapterTitle")) ||
                        device.hasObject(By.desc("顶部栏标题：第1章 甲 $chapterTitle"))
                    val saved = anchors.loadAll().singleOrNull()
                    if (headerMatches && saved?.anchor?.chapterIndex == chapter &&
                        saved.anchor.charOffset == 0 && saved.readingMode == ReadingMode.PAGE) break
                    delay(50)
                }
                true
            } ?: false
        }
        if (!confirmed) {
            val headers = runCatching {
                (device.findObjects(By.descContains("顶部栏标题")) + device.findObjects(By.descContains("沉浸式章节")))
                    .map { it.contentDescription }
            }.getOrElse { listOf(it.toString()) }
            val visibleText = runCatching {
                device.findObjects(By.clazz("android.widget.TextView")).map { "${it.text?.take(80)}@${it.visibleBounds}" }
            }.getOrElse { listOf(it.toString()) }
            fail("正文换章后顶栏也必须确认同一章节；目标=$chapter；磁盘=${anchors.loadAll()}；头部=$headers；正文/错误=$visibleText")
        }
    }

    @Test
    fun consecutiveForwardForwardBackwardAccumulatesBeforeAnimationSettles() {
        withThreeChapters { device ->
            val y = device.displayHeight / 2
            device.click(device.displayWidth * 9 / 10, y)
            device.click(device.displayWidth * 9 / 10, y)
            device.click(device.displayWidth / 10, y)
            assertTrue("连续两次前进一次后退应停在乙章", device.wait(Until.hasObject(By.textContains("乙章唯一正文")), 5_000))
        }
    }

    @Test
    fun consecutiveSwipesRetainAcceptedTurnsWhenTheNextFingerInterruptsAnimation() {
        withThreeChapters { device ->
            val y = device.displayHeight / 2
            val left = device.displayWidth / 10
            val right = device.displayWidth * 8 / 10
            // No per-step content or idle wait: each release is an input, regardless of old animation.
            device.swipe(right, y, left, y, 6)
            device.swipe(right, y, left, y, 6)
            device.swipe(left, y, right, y, 6)
            // Only wait after the complete sequence, so the first visit to B cannot pass the test.
            device.waitForIdle()
            assertTrue("连续前、前、后滑动应停在乙章", device.wait(Until.hasObject(By.textContains("乙章唯一正文")), 8_000))
            assertConfirmedChapter(device, 1, "乙")
            assertTrue("三次滑动及确认结束后仍应显示乙章正文", device.hasObject(By.textContains("乙章唯一正文")))
        }
    }

    @Test
    fun cancellingTheNextDragDoesNotWithdrawAnAlreadyAcceptedTurn() {
        withThreeChapters { device ->
            val y = device.displayHeight / 2
            device.swipe(device.displayWidth * 8 / 10, y, device.displayWidth / 10, y, 6)
            device.swipe(device.displayWidth * 80 / 100, y, device.displayWidth * 76 / 100, y, 100)
            assertTrue("新的短拖动取消后，先前已接受的翻页仍应到乙章", device.wait(Until.hasObject(By.textContains("乙章唯一正文")), 8_000))
            assertConfirmedChapter(device, 1, "乙")
        }
    }

    @Test
    fun previousAtBookStartThenNextMovesToSecondChapter() {
        withThreeChapters { device ->
            // Retain the settings round-trip involved in the observed boundary-test failure.
            assertTrue(device.ensurePageMode())
            assertConfirmedChapter(device, 0, "甲")
            val y = device.displayHeight / 2
            device.click(device.displayWidth / 10, y)
            device.click(device.displayWidth * 9 / 10, y)
            val arrived = device.wait(Until.hasObject(By.textContains("乙章唯一正文")), 8_000)
            val context = ApplicationProvider.getApplicationContext<Context>()
            assertTrue("书首上一页、下一页应到乙章；实际磁盘位置=${FileAnchorStore(context.filesDir).loadAll()}", arrived)
            assertConfirmedChapter(device, 1, "乙")
        }
    }

    @Test
    fun nextAfterSelectingSecondChapterInDirectoryMovesToThirdChapter() {
        withThreeChapters { device ->
            assertTrue(device.revealReaderChrome("目录"))
            assertTrue(device.tapPrimaryAction(ReaderPrimaryActionSlot.DIRECTORY))
            val chapterTwo = device.wait(Until.findObject(By.text("第2章 乙")), 3_000)
            assertNotNull("目录应列出乙章", chapterTwo)
            assertTrue(device.clickObjectCenter(chapterTwo!!))
            assertTrue(device.wait(Until.hasObject(By.textContains("乙章唯一正文")), 5_000))

            device.click(device.displayWidth * 9 / 10, device.displayHeight / 2)
            assertTrue("目录选择乙章后的下一页应从乙章出发进入丙章", device.wait(Until.hasObject(By.textContains("丙章唯一正文")), 5_000))
        }
    }

    @Test
    fun slowShortDragSpringsBackWithoutTurningOnRelease() {
        withThreeChapters { device ->
            val y = device.displayHeight / 2
            // Cross touch slop, but stay well below a page turn and end inside the right tap zone.
            device.swipe(device.displayWidth * 80 / 100, y, device.displayWidth * 76 / 100, y, 100)
            device.waitForIdle()
            assertFalse("短距离拖动松手不应被当作右侧点击翻到乙章", device.wait(Until.hasObject(By.textContains("乙章唯一正文")), 1_200))
            assertTrue("回弹后仍应显示原来的甲章正文", device.hasObject(By.textContains("甲章唯一正文")))
        }
    }

    private fun withThreeChapters(block: (UiDevice) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        resetStoryAppState(context)
        val file = File(context.cacheDir, "navigation-sequence.txt").apply {
            writeText("第1章 甲\n甲章唯一正文。\n第2章 乙\n乙章唯一正文。\n第3章 丙\n丙章唯一正文。")
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch<MainActivity>(intent).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("甲章唯一正文")), 8_000))
            assertEquals(ReadingMode.PAGE, ReaderSettingsStore(context.filesDir).load().readingMode)
            assertConfirmedChapter(device, 0, "甲")
            block(device)
        }
    }
}
