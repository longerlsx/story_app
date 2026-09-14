package com.longerlsx.storyapp.feature.reader

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.longerlsx.storyapp.MainActivity
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.data.book.FileAnchorStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPositionRestoreTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun activityRelaunchFontChangeAndRotationKeepExactSavedScrollLine() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        resetStoryAppState(context)
        val application = context.applicationContext as StoryApplication
        val importFile = File(context.cacheDir, "reader-position-restore.txt").apply {
            writeText(buildString {
                appendLine("《位置恢复测试》")
                appendLine("第1章 长章")
                for (index in 1..220) appendLine("正文段落-${index.toString().padStart(3, '0')}")
            })
        }
        val externalIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            setDataAndType(
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", importFile),
                "text/plain",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun openSettings() {
            if (!hasVisibleNode(hasText("设置"))) {
                composeRule.onRoot().performTouchInput { click(center) }
                composeRule.waitUntil(timeoutMillis = 3_000) { hasVisibleNode(hasText("设置")) }
            }
            composeRule.onNodeWithText("设置").performTouchInput { click(center) }
            composeRule.waitUntil(timeoutMillis = 3_000) { hasVisibleNode(hasText("亮度")) }
        }
        fun closeSettings() {
            composeRule.onNodeWithText("设置").performTouchInput { click(center) }
            composeRule.waitUntil(timeoutMillis = 3_000) { !hasVisibleNode(hasText("亮度")) }
        }
        var stoppedAt = 0L
        ActivityScenario.launch<MainActivity>(externalIntent).use {
            composeRule.waitUntil(timeoutMillis = 8_000) { hasVisibleNode(hasText("正文段落-001")) }
            openSettings()
            composeRule.onNodeWithContentDescription("阅读模式：滚动", substring = true)
                .performTouchInput { click(center) }
            composeRule.waitUntil(timeoutMillis = 3_000) { hasVisibleNode(hasContentDescription("阅读模式：滚动，已选中")) }
            closeSettings()
            composeRule.onRoot().performTouchInput { click(center) }
            composeRule.waitUntil(timeoutMillis = 3_000) { !hasVisibleNode(hasText("设置")) }
            composeRule.waitUntil(timeoutMillis = 8_000) {
                assertFalse("滚动首屏定位不能超时", hasVisibleNode(hasText("定位等待超时", substring = true)))
                FileAnchorStore(context.filesDir).loadAll().singleOrNull()?.readingMode == ReadingMode.SCROLL
            }
            repeat(18) {
                if (!hasVisibleNode(hasText("正文段落-120"))) {
                    composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
                        .performTouchInput {
                            swipe(Offset(center.x, height * 0.8f), Offset(center.x, height * 0.25f), durationMillis = 400)
                        }
                    composeRule.waitForIdle()
                }
            }
            assertTrue("真实滚动应到达样本中段", hasVisibleNode(hasText("正文段落-120")))
            composeRule.waitForIdle()
            stoppedAt = System.currentTimeMillis()
        }

        // Observe the real ON_STOP write, rather than constructing or injecting a test anchor.
        val diskAnchors = FileAnchorStore(context.filesDir)
        var saved: ReadingProgress? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            saved = diskAnchors.loadAll().singleOrNull()
            saved?.let { it.updatedAt >= stoppedAt && it.anchor.charOffset > 0 } == true
        }
        val progress = checkNotNull(saved)
        assertEquals(ReadingMode.SCROLL, progress.readingMode)
        val chapterText = runBlocking {
            checkNotNull(application.bookRepository.getChapterText(progress.bookId, progress.anchor.chapterIndex))
        }
        assertTrue(progress.anchor.charOffset in chapterText.indices)
        val lineStart = chapterText.lastIndexOf('\n', startIndex = progress.anchor.charOffset - 1) + 1
        val lineEnd = chapterText.indexOf('\n', startIndex = progress.anchor.charOffset)
            .takeIf { it >= 0 } ?: chapterText.length
        val targetLine = chapterText.substring(lineStart, lineEnd)
        assertTrue("保存位置必须对应样本中唯一的正文行", targetLine.startsWith("正文段落-"))

        // This covers a new Activity and layout, not host process death.
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).use {
            composeRule.waitUntil(timeoutMillis = 5_000) { hasVisibleNode(hasText(targetLine)) }
            assertSavedLineAtReadableTop(targetLine, context.resources.displayMetrics.density)

            openSettings()
            composeRule.onAllNodesWithText("A+")[0].performTouchInput { click(center) }
            composeRule.waitUntil(timeoutMillis = 3_000) { hasVisibleNode(hasContentDescription("字号：20")) }
            closeSettings()
            assertSavedLineAtReadableTop(targetLine, context.resources.displayMetrics.density)

            try {
                device.setOrientationLeft()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(
                        SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                        .any { it.boundsInRoot.width > it.boundsInRoot.height }
                }
                composeRule.waitUntil(timeoutMillis = 5_000) { hasVisibleNode(hasText(targetLine)) }
                assertSavedLineAtReadableTop(targetLine, context.resources.displayMetrics.density)
            } finally {
                try {
                    device.setOrientationNatural()
                } finally {
                    device.unfreezeRotation()
                }
            }
        }
    }

    private fun hasVisibleNode(matcher: SemanticsMatcher): Boolean {
        val viewport = composeRule.onAllNodes(isRoot(), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).singleOrNull()?.boundsInRoot ?: return false
        return composeRule.onAllNodes(matcher, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).any { node ->
                val clipped = node.boundsInRoot
                val origin = node.positionInRoot
                // Off-screen paragraph semantics can exist: require the complete node inside its clip and viewport.
                clipped.width > 0f && clipped.height > 0f &&
                    clipped.width + 1f >= node.size.width && clipped.height + 1f >= node.size.height &&
                    origin.x >= viewport.left - 1f && origin.y >= viewport.top - 1f &&
                    origin.x + node.size.width <= viewport.right + 1f &&
                    origin.y + node.size.height <= viewport.bottom + 1f
            }
    }

    private fun assertSavedLineAtReadableTop(targetLine: String, density: Float) {
        composeRule.waitForIdle()
        val target = composeRule.onNodeWithText(targetLine, useUnmergedTree = true).fetchSemanticsNode()
        val scrollNodes = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("关闭面板后应只有正文滚动阅读区", 1, scrollNodes.size)
        val viewport = scrollNodes.single().boundsInRoot
        composeRule.runOnIdle {
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(target.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
            val textLayout = layouts.single()
            assertEquals(targetLine, textLayout.layoutInput.text.text)
            assertEquals("短段落样本必须实际排成一行", 1, textLayout.lineCount)
            assertEquals(targetLine.length, textLayout.getLineEnd(0, visibleEnd = true))
            assertFalse("目标正文行不可在自身布局中裁切", textLayout.hasVisualOverflow)
            val lineTop = target.positionInRoot.y + textLayout.getLineTop(0)
            val lineBottom = target.positionInRoot.y + textLayout.getLineBottom(0)
            val lineLeft = target.positionInRoot.x + textLayout.getLineLeft(0)
            val lineRight = target.positionInRoot.x + textLayout.getLineRight(0)
            // The reading area leaves 8 dp above its first readable line; only pixel rounding is tolerated.
            assertEquals("必须恢复保存位置对应的确切行并对齐阅读区顶部", viewport.top + 8f * density, lineTop, 1.5f)
            assertTrue("目标行顶部必须完整可见", lineTop >= viewport.top - 1f)
            assertTrue("目标行底部必须完整可见", lineBottom <= viewport.bottom + 1f)
            assertTrue("目标行左侧必须完整可见", lineLeft >= viewport.left - 1f)
            assertTrue("目标行右侧必须完整可见", lineRight <= viewport.right + 1f)
        }
    }
}
