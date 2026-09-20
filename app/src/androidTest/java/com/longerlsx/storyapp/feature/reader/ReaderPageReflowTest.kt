package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.book.BookRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPageReflowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cachedBoundaryPagesConfirmWhileOuterChapterStillHoldsTheCalculationLock() {
        val texts = listOf("甲章末页唯一正文。", "乙章首页唯一正文。", "丙章外围正文。")
        val book = Book("cached-boundary", "缓存章界", null, ImportSourceType.LOCAL_FILE,
            "cached.txt", "unused-cached-source", "UTF-8", "cached-hash",
            texts.sumOf(String::length), 3, 0L, 0L)
        val chapters = texts.mapIndexed { index, text ->
            Chapter(book.id, index, "第${index + 1}章", index * 30, index * 30 + text.length, text.length)
        }
        val delegate = InMemoryBookRepository()
        runBlocking { delegate.saveImportedBook(book, chapters, texts.mapIndexed { index, text -> index to text }.toMap()) }
        val reads = List(3) { AtomicInteger() }
        val outerEntered = CompletableDeferred<Unit>()
        val outerRelease = CompletableDeferred<Unit>()
        val outerReadFinished = CompletableDeferred<Unit>()
        val repository = object : BookRepository by delegate {
            override suspend fun getChapterText(bookId: String, chapterIndex: Int): String? {
                reads[chapterIndex].incrementAndGet()
                if (chapterIndex != 2) return delegate.getChapterText(bookId, chapterIndex)
                try {
                    withContext(NonCancellable) {
                        outerEntered.complete(Unit)
                        outerRelease.await()
                    }
                    return delegate.getChapterText(bookId, chapterIndex)
                } finally {
                    outerReadFinished.complete(Unit)
                }
            }
        }
        val position = ReaderPositionState(ReadingAnchor(0, 0))
        composeRule.setContent {
            val settings = ReaderSettings(fontSizeSp = 18)
            Box(Modifier.requiredSize(320.dp, 480.dp).testTag("cached-boundary-reader")) {
                StablePageReaderContent(
                    bookId = book.id, chapters = chapters, repository = repository, position = position,
                    themePalette = ReaderThemeResolver.resolveActivePalette(settings),
                    fontSize = 18.sp, lineHeight = 27.sp, paragraphSpacing = 12.dp,
                    activeHighlight = null, followTargetCharOffset = null,
                    enableLongPress = false, dismissExpandedChrome = false,
                    onRestartFromLocation = { _, _ -> }, onManualFollowInterruption = {},
                    onCrossChapter = {}, onConfirmed = { _, _, _ -> }, onToggleChrome = {},
                )
            }
        }
        fun tap(direction: Int) = composeRule.onNodeWithTag("cached-boundary-reader").performTouchInput {
            click(Offset(width * (if (direction > 0) 0.9f else 0.1f), height * 0.5f))
        }
        fun awaitChapter(chapter: Int) {
            composeRule.waitUntil(10_000) {
                position.request == null && position.hasConfirmedLayout && position.confirmedAnchor == ReadingAnchor(chapter, 0)
            }
            composeRule.onNodeWithText(texts[chapter]).assertIsDisplayed()
        }
        try {
            awaitChapter(0)
            tap(1)
            awaitChapter(1)
            composeRule.waitUntil(10_000) { outerEntered.isCompleted }
            val cachedReadCounts = reads.take(2).map { it.get() }
            tap(-1)
            awaitChapter(0)
            tap(1)
            awaitChapter(1)
            composeRule.runOnIdle {
                assertTrue("A/B往返确认时外围C仍必须占用计算锁", !outerRelease.isCompleted && !outerReadFinished.isCompleted)
                assertEquals("已缓存A/B往返不应重新读取并分页", cachedReadCounts, reads.take(2).map { it.get() })
                assertNull(position.error)
            }
        } finally {
            outerRelease.complete(Unit)
            if (outerEntered.isCompleted) runBlocking { withTimeout(5_000) { outerReadFinished.await() } }
        }
    }

    @Test
    fun oneSwipeWaitsForUnpreparedNextChapterWithoutNeedingAnotherSwipe() {
        val texts = listOf("甲章唯一正文。", "乙章唯一正文。" + "远山近水，风吹过河岸。".repeat(200))
        val book = Book("cold-neighbor", "未准备邻章", null, ImportSourceType.LOCAL_FILE,
            "cold.txt", "unused-cold-source", "UTF-8", "cold-hash",
            texts.sumOf(String::length), 2, 0L, 0L)
        val chapters = texts.mapIndexed { index, text ->
            Chapter(book.id, index, "第${index + 1}章", index * 20, index * 20 + text.length, text.length)
        }
        val delegate = InMemoryBookRepository()
        runBlocking { delegate.saveImportedBook(book, chapters, texts.mapIndexed { index, text -> index to text }.toMap()) }
        val nextChapterReady = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val repository = object : BookRepository by delegate {
            override suspend fun getChapterText(bookId: String, chapterIndex: Int): String? {
                if (chapterIndex == 1) {
                    reads.incrementAndGet()
                    nextChapterReady.await()
                }
                return delegate.getChapterText(bookId, chapterIndex)
            }
        }
        val position = ReaderPositionState(ReadingAnchor(0, 0))
        composeRule.setContent {
            val settings = ReaderSettings(fontSizeSp = 18)
            Box(Modifier.requiredSize(320.dp, 480.dp).testTag("cold-reader")) {
                StablePageReaderContent(
                    bookId = book.id, chapters = chapters, repository = repository, position = position,
                    themePalette = ReaderThemeResolver.resolveActivePalette(settings),
                    fontSize = 18.sp, lineHeight = 27.sp, paragraphSpacing = 12.dp,
                    activeHighlight = null, followTargetCharOffset = null,
                    enableLongPress = false, dismissExpandedChrome = false,
                    onRestartFromLocation = { _, _ -> }, onManualFollowInterruption = {},
                    onCrossChapter = {}, onConfirmed = { _, _, _ -> }, onToggleChrome = {},
                )
            }
        }
        try {
            composeRule.waitUntil(10_000) { position.hasConfirmedLayout && reads.get() > 0 }
            composeRule.onNodeWithText(texts[0]).assertIsDisplayed()
            composeRule.onNodeWithTag("cold-reader").performTouchInput {
                swipe(Offset(width * 0.85f, height * 0.5f), Offset(width * 0.1f, height * 0.5f), 200)
            }
            nextChapterReady.complete(Unit)
            composeRule.waitUntil(10_000) {
                position.error != null || (position.request == null && position.confirmedAnchor.chapterIndex == 1)
            }
            composeRule.runOnIdle {
                assertNull(position.error)
                assertEquals(ReadingAnchor(1, 0), position.confirmedAnchor)
            }
        } finally {
            nextChapterReady.complete(Unit)
        }
    }

    @Test
    fun pendingTurnsSurviveReflowAndDirectoryJumpReplacesOlderNavigation() {
        val texts = listOf("甲章短正文。", "乙章短正文。", "丙章短正文。")
        val book = Book("pending-turns", "连续导航", null, ImportSourceType.LOCAL_FILE,
            "pending.txt", "unused-pending-source", "UTF-8", "pending-hash",
            texts.sumOf(String::length), 3, 0L, 0L)
        val chapters = texts.mapIndexed { index, text ->
            Chapter(book.id, index, "第${index + 1}章", index * 20, index * 20 + text.length, text.length)
        }
        val delegate = InMemoryBookRepository()
        runBlocking { delegate.saveImportedBook(book, chapters, texts.mapIndexed { index, text -> index to text }.toMap()) }
        val bodyGate = AtomicReference(CompletableDeferred<Unit>())
        val bReads = AtomicInteger()
        val repository = object : BookRepository by delegate {
            override suspend fun getChapterText(bookId: String, chapterIndex: Int): String? {
                if (chapterIndex == 1) {
                    val pending = bodyGate.get()
                    bReads.incrementAndGet()
                    pending.await()
                }
                return delegate.getChapterText(bookId, chapterIndex)
            }
        }
        val position = ReaderPositionState(ReadingAnchor(0, 0))
        val fontSize = mutableIntStateOf(18)
        composeRule.setContent {
            val settings = ReaderSettings(fontSizeSp = fontSize.intValue)
            Box(Modifier.requiredSize(320.dp, 480.dp).testTag("pending-reader")) {
                StablePageReaderContent(
                    bookId = book.id, chapters = chapters, repository = repository, position = position,
                    themePalette = ReaderThemeResolver.resolveActivePalette(settings),
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                    paragraphSpacing = (settings.fontSizeSp * settings.paragraphSpacingEm).coerceIn(8f, 36f).dp,
                    activeHighlight = null, followTargetCharOffset = null,
                    enableLongPress = false, dismissExpandedChrome = false,
                    onRestartFromLocation = { _, _ -> }, onManualFollowInterruption = {},
                    onCrossChapter = {}, onConfirmed = { _, _, _ -> }, onToggleChrome = {},
                )
            }
        }
        fun tap(direction: Int) {
            composeRule.onNodeWithTag("pending-reader").performTouchInput {
                click(Offset(width * (if (direction > 0) 0.9f else 0.1f), height * 0.5f))
            }
        }
        fun awaitChapter(chapter: Int) {
            composeRule.waitUntil(10_000) {
                position.error != null || (position.hasConfirmedLayout && position.request == null &&
                    position.confirmedAnchor.chapterIndex == chapter)
            }
            composeRule.runOnIdle {
                assertNull(position.error)
                assertEquals(ReadingAnchor(chapter, 0), position.confirmedAnchor)
            }
            composeRule.onNodeWithText(texts[chapter]).assertIsDisplayed()
        }

        try {
            awaitChapter(0)
            tap(1)
            composeRule.waitUntil(10_000) { bReads.get() > 0 }
            tap(1)
            tap(-1)
            composeRule.runOnIdle {
                assertEquals(listOf(1, 1, -1), position.request?.turns?.map { it.direction })
                fontSize.intValue = 32
            }
            composeRule.waitForIdle()
            bodyGate.get().complete(Unit)
            awaitChapter(1)

            tap(-1)
            awaitChapter(0)
            // A new layout forces a fresh, cancellable B load instead of reusing the completed page cache.
            bodyGate.set(CompletableDeferred<Unit>())
            val readsBefore = bReads.get()
            composeRule.runOnIdle {
                fontSize.intValue = 18
                position.reflow()
            }
            awaitChapter(0)
            tap(1)
            composeRule.waitUntil(10_000) { bReads.get() > readsBefore }
            tap(1)
            composeRule.runOnIdle {
                assertEquals(listOf(1, 1), position.request?.turns?.map { it.direction })
                position.jump(ReadingAnchor(0, 0))
            }
            tap(1)
            composeRule.runOnIdle {
                assertEquals(listOf(1), position.request?.turns?.map { it.direction })
            }
            bodyGate.get().complete(Unit)
            awaitChapter(1)
        } finally {
            bodyGate.get().complete(Unit)
        }
    }

    @Test
    fun shrinkingAndEnlargingFontPreservesTheRequestedCharacter() {
        val targetOffset = 1_207
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张。".repeat(100).take(targetOffset) +
            "锚点目标不应漂移。" + "春夏秋冬，远处河岸的灯依次亮起。".repeat(90)
        val book = Book(
            "reflow-book", "重排测试", null, ImportSourceType.LOCAL_FILE,
            "reflow.txt", "unused-reflow-source", "UTF-8", "reflow-hash", content.length, 1, 0L, 0L,
        )
        val chapter = Chapter(book.id, 0, "正文", 0, content.length, content.length)
        val repository = InMemoryBookRepository()
        runBlocking { repository.saveImportedBook(book, listOf(chapter), mapOf(0 to content)) }
        val target = ReadingAnchor(0, targetOffset)
        val position = ReaderPositionState(target)
        val fontSize = mutableIntStateOf(32)
        val confirmations = AtomicInteger()
        var confirmedPage: ReaderPageSlice? = null

        composeRule.setContent {
            val settings = ReaderSettings(fontSizeSp = fontSize.intValue)
            Box(Modifier.requiredSize(320.dp, 480.dp).testTag("reflow-viewport")) {
                StablePageReaderContent(
                    bookId = book.id,
                    chapters = listOf(chapter),
                    repository = repository,
                    position = position,
                    themePalette = ReaderThemeResolver.resolveActivePalette(settings),
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                    paragraphSpacing = (settings.fontSizeSp * settings.paragraphSpacingEm).coerceIn(8f, 36f).dp,
                    activeHighlight = null,
                    followTargetCharOffset = null,
                    enableLongPress = false,
                    dismissExpandedChrome = false,
                    onRestartFromLocation = { _, _ -> },
                    onManualFollowInterruption = {},
                    onCrossChapter = {},
                    onConfirmed = { _, pages, index ->
                        confirmedPage = pages[index]
                        confirmations.incrementAndGet()
                    },
                    onToggleChrome = {},
                )
            }
        }

        listOf(32, 18, 32).forEachIndexed { index, size ->
            if (index > 0) composeRule.runOnIdle { fontSize.intValue = size }
            composeRule.waitUntil(10_000) {
                position.error != null || (position.request == null && confirmations.get() >= index + 1)
            }
            composeRule.runOnIdle {
                assertNull(position.error)
                val page = requireNotNull(confirmedPage)
                assertTrue("字号 $size 的可见页面必须包含原目标文字", targetOffset in page.visibleStartCharOffset until page.visibleEndCharOffset)
                assertEquals("字号 $size 不能把目标位置改成页首", target, position.confirmedAnchor)
            }

            val viewport = composeRule.onNodeWithTag("reflow-viewport").fetchSemanticsNode().boundsInRoot
            val nodes = composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            composeRule.runOnIdle {
                val visibleTarget = nodes.any { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    layouts.any { layout ->
                        val offset = layout.layoutInput.text.text.indexOf('锚')
                        if (offset < 0 || layout.layoutInput.style.fontSize != size.sp) {
                            false
                        } else {
                            val glyph = layout.getBoundingBox(offset).translate(node.positionInRoot)
                            glyph.left >= viewport.left && glyph.right <= viewport.right &&
                                glyph.top >= viewport.top && glyph.bottom <= viewport.bottom
                        }
                    }
                }
                assertTrue("字号 $size 的真实已布局正文必须在视口内显示目标字符", visibleTarget)
            }
        }
    }
}
