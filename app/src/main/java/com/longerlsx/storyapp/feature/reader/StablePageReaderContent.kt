package com.longerlsx.storyapp.feature.reader

import android.os.Trace
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.data.book.BookRepository
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsActiveVisualRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign

/** A chapter window shares one native pager for taps, drags and chapter boundaries. */
@Composable
internal fun StablePageReaderContent(
    bookId: String,
    chapters: List<Chapter>,
    repository: BookRepository,
    position: ReaderPositionState,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    paragraphSpacing: Dp,
    activeHighlight: ReaderTtsActiveVisualRange?,
    followTargetCharOffset: Int?,
    enableLongPress: Boolean,
    dismissExpandedChrome: Boolean,
    onRestartFromLocation: (Int, Int) -> Unit,
    onManualFollowInterruption: () -> Unit,
    onCrossChapter: () -> Unit,
    onConfirmed: (Int, List<ReaderPageSlice>, Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val fontResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    val calculations = remember(bookId, repository, fontResolver, layoutDirection) {
        ChapterPageCalculations(bookId, repository, fontResolver, layoutDirection)
    }
    val display = remember(bookId) { PageWindowDisplay() }
    val confirmedCallback by rememberUpdatedState(onConfirmed)
    val crossChapterCallback by rememberUpdatedState(onCrossChapter)
    val interruptCallback by rememberUpdatedState(onManualFollowInterruption)
    val toggleCallback by rememberUpdatedState(onToggleChrome)
    val restartCallback by rememberUpdatedState(onRestartFromLocation)
    val dismissChrome by rememberUpdatedState(dismissExpandedChrome)
    val chapterIndices = remember(chapters) { chapters.map { it.chapterIndex } }

    BoxWithConstraints(Modifier.fillMaxSize().background(themePalette.background)) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val viewport = ReaderPageViewportMetricsResolver.resolve(
            containerHeightPx = with(density) { maxHeight.roundToPx() },
            baseTopPaddingPx = with(density) { ReaderPageTopPadding.roundToPx() },
            baseBottomPaddingPx = with(density) { ReaderPageBottomPadding.roundToPx() },
            systemTopInsetPx = with(density) { insets.calculateTopPadding().roundToPx() },
            systemBottomInsetPx = with(density) { insets.calculateBottomPadding().roundToPx() },
        )
        val layout = ReaderPageLayoutKey(
            widthPx = with(density) { (maxWidth - ReaderHorizontalPadding * 2).roundToPx() },
            heightPx = viewport.availableHeightPx,
            density = density.density,
            fontScale = density.fontScale,
            fontSizeSp = fontSize.value.toInt(),
            lineHeightMultiplier = lineHeight.value / fontSize.value,
            paragraphSpacingPx = with(density) { paragraphSpacing.toPx() },
        )
        val appearance = PageAppearance(
            layout, fontSize, lineHeight, paragraphSpacing,
            with(density) { viewport.topPaddingPx.toDp() },
            with(density) { viewport.bottomPaddingPx.toDp() },
        )
        val pager = rememberPagerState { display.window?.pages?.size ?: 0 }
        val request = position.request

        fun installWindow(window: ChapterPageWindow, visible: WindowPage) {
            if (display.window == window) return
            val placed = if (display.window?.appearance?.layout == window.appearance.layout) {
                // Rebased pages keep the same measured layout; cached children need not be placed again.
                display.placedPages.intersect(window.pages.map { it.id }.toSet())
            } else emptySet()
            display.window = window
            display.placedPages = placed
            // The same text keeps its identity when a boundary preview becomes the center chapter.
            pager.requestScrollToPage(window.pages.indexOfFirst { it.id == visible.id }.coerceAtLeast(0))
        }

        fun centeredWindow(batch: ChapterPages, look: PageAppearance): ChapterPageWindow {
            val at = chapterIndices.indexOf(batch.chapterIndex)
            return ChapterPageWindow(
                center = batch,
                previous = chapterIndices.getOrNull(at - 1)?.let { calculations.cached(it, look.layout) },
                next = chapterIndices.getOrNull(at + 1)?.let { calculations.cached(it, look.layout) },
                appearance = look,
            )
        }

        fun recordPage(page: WindowPage, requestId: Long? = null, targetAnchor: ReadingAnchor? = null) {
            val previousChapter = position.confirmedAnchor.chapterIndex
            val anchor = targetAnchor ?: ReadingAnchor(page.chapter.chapterIndex, page.slice.visibleStartCharOffset)
            val accepted = if (requestId != null) position.confirm(requestId, anchor) else position.recordViewport(anchor)
            if (!accepted) return
            display.window?.let { window ->
                installWindow(centeredWindow(page.chapter, window.appearance), page)
            }
            display.confirmedWindow = display.window
            display.confirmedPageId = page.id
            calculations.retain(chapterIndices, page.chapter.chapterIndex, setOf(page.chapter.layout))
            if (previousChapter != anchor.chapterIndex) crossChapterCallback()
            confirmedCallback(anchor.chapterIndex, page.chapter.pages, page.pageIndex)
        }

        LaunchedEffect(layout) {
            if (display.window != null && display.window?.appearance?.layout != layout && position.request == null) {
                position.reflow()
            }
        }

        LaunchedEffect(position.error) {
            if (position.error != null) {
                val previous = display.confirmedWindow ?: return@LaunchedEffect
                val page = previous.pages.firstOrNull { it.id == display.confirmedPageId } ?: return@LaunchedEffect
                installWindow(previous, page)
                pager.requestScrollToPage(previous.pages.indexOf(page))
            }
        }

        LaunchedEffect(request?.id, layout, chapterIndices, calculations) {
            val intent = request ?: return@LaunchedEffect
            if (layout.widthPx <= 0 || layout.heightPx <= 0) return@LaunchedEffect
            try {
                calculations.retain(chapterIndices, intent.anchor.chapterIndex, intent.turns.map { it.layout }.toSet() + layout)
                val resolved = calculations.resolve(intent, layout, chapterIndices)
                val target = resolved.page
                snapshotFlow { !display.userGesture && !pager.isScrollInProgress }.first { it }
                if (position.request?.id != intent.id) return@LaunchedEffect
                val oldWindow = display.window
                val existingIndex = oldWindow?.takeIf { it.appearance.layout == layout }
                    ?.pages?.indexOfFirst { it.id == target.id } ?: -1
                if (existingIndex >= 0) {
                    pager.animateScrollToPage(existingIndex)
                } else {
                    installWindow(centeredWindow(target.chapter, appearance), target)
                }
                // Layout, not a computed page number, acknowledges a navigation request.
                snapshotFlow {
                    val window = display.window
                    !display.userGesture && !pager.isScrollInProgress &&
                        window?.pages?.getOrNull(pager.settledPage)?.id == target.id &&
                        target.id in display.placedPages
                }.first { it }
                recordPage(target, intent.id, resolved.anchor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                position.fail(intent.id, failure.message ?: "当前章节排版失败，请重试。")
            }
        }

        // Optional neighbors follow the visible target; a new request cancels this work first.
        LaunchedEffect(display.window?.center, position.request?.id, calculations) {
            if (position.request != null) return@LaunchedEffect
            val initial = display.window ?: return@LaunchedEffect
            val at = chapterIndices.indexOf(initial.center.chapterIndex)
            for (neighbor in listOfNotNull(chapterIndices.getOrNull(at + 1), chapterIndices.getOrNull(at - 1))) {
                try {
                    calculations.pages(neighbor, initial.appearance.layout)
                    snapshotFlow { !display.userGesture && !pager.isScrollInProgress }.first { it }
                    val current = display.window ?: return@LaunchedEffect
                    if (position.request != null || current.center != initial.center) return@LaunchedEffect
                    val visible = current.pages.getOrNull(pager.settledPage) ?: return@LaunchedEffect
                    installWindow(centeredWindow(current.center, current.appearance), visible)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the readable page. Explicit navigation retries and reports a target failure.
                }
            }
        }

        LaunchedEffect(followTargetCharOffset, activeHighlight?.chapterIndex, position.request?.id) {
            val offset = followTargetCharOffset ?: return@LaunchedEffect
            if (position.request != null) return@LaunchedEffect
            val window = display.window ?: return@LaunchedEffect
            val visible = window.pages.getOrNull(pager.settledPage) ?: return@LaunchedEffect
            val chapter = activeHighlight?.chapterIndex ?: position.confirmedAnchor.chapterIndex
            if (chapter != visible.chapter.chapterIndex ||
                ReaderPageAnchorMapper.pageIndexForCharOffset(visible.chapter.pages, offset) != visible.pageIndex
            ) {
                position.jump(ReadingAnchor(chapter, offset))
            }
        }

        val nativeFling = PagerDefaults.flingBehavior(pager)
        val flingBehavior = remember(nativeFling, display, position) {
            object : TargetedFlingBehavior {
                override suspend fun ScrollScope.performFling(
                    initialVelocity: Float,
                    onRemainingDistanceUpdated: (Float) -> Unit,
                ): Float {
                    val gestureId = display.gesture?.id
                    try {
                        val remaining = with(nativeFling) {
                            performFling(initialVelocity, onRemainingDistanceUpdated)
                        }
                        currentCoroutineContext().ensureActive()
                        display.gesture?.takeIf { it.id == gestureId }?.let {
                            display.gesture = it.copy(flingFinished = true)
                        }
                        return remaining
                    } catch (cancelled: CancellationException) {
                        if (display.gesture?.id == gestureId) {
                            display.gesture = null
                            if (position.request != null) position.reflow()
                        }
                        throw cancelled
                    }
                }
            }
        }

        LaunchedEffect(pager, display, position) {
            pager.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        val window = display.window ?: return@collect
                        display.gesture = PageGesture(display.nextGestureId++, window, pager.settledPage)
                        interruptCallback()
                    }
                    is DragInteraction.Stop -> {
                        // Pointer release precedes the native fling; its brief idle is not a destination.
                        display.gesture?.let { display.gesture = it.copy(released = true) }
                    }
                    is DragInteraction.Cancel -> {
                        display.gesture = null
                        if (position.request != null) position.reflow()
                    }
                }
            }
        }

        val gesture = display.gesture
        LaunchedEffect(gesture?.id, gesture?.released, gesture?.flingFinished) {
            val completed = gesture?.takeIf { it.released && it.flingFinished } ?: return@LaunchedEffect
            try {
                // The native pager may finish adjusting its page after our fling delegate returns.
                snapshotFlow { !pager.isScrollInProgress }.first { it }
                if (display.gesture?.id != completed.id) return@LaunchedEffect
                val current = display.window ?: return@LaunchedEffect
                val sourceId = completed.window.pages.getOrNull(completed.startingPage)?.id
                val startingIndex = current.pages.indexOfFirst { it.id == sourceId }
                if (startingIndex < 0) {
                    position.reflow()
                    return@LaunchedEffect
                }
                val distance = pager.settledPage - startingIndex
                repeat(abs(distance)) { position.turn(distance.sign, completed.window.appearance.layout) }
                if (distance == 0 && position.request != null) position.reflow()
                // The shared request path checks actual placement, handles timeout and saves progress.
            } finally {
                if (display.gesture?.id == completed.id) display.gesture = null
            }
        }

        HorizontalPager(
            state = pager,
            flingBehavior = flingBehavior,
            key = { index -> checkNotNull(display.window).pages[index].id },
            modifier = Modifier.fillMaxSize().readerBodyTapInput { offset, size ->
                if (dismissChrome) {
                    toggleCallback()
                } else {
                    when (ReaderTapZone.resolve(offset.x, size.width.toFloat())) {
                        ReaderTapZone.TOGGLE_CHROME -> toggleCallback()
                        ReaderTapZone.PREVIOUS -> {
                            interruptCallback()
                            position.turn(-1, display.window?.appearance?.layout ?: layout)
                        }
                        ReaderTapZone.NEXT -> {
                            interruptCallback()
                            position.turn(1, display.window?.appearance?.layout ?: layout)
                        }
                    }
                }
            },
        ) { index ->
            val window = display.window ?: return@HorizontalPager
            val page = window.pages.getOrNull(index) ?: return@HorizontalPager
            val look = window.appearance
            ReaderPageSurface(
                page = page.slice,
                themePalette = themePalette,
                fontSize = look.fontSize,
                lineHeight = look.lineHeight,
                paragraphSpacing = look.paragraphSpacing,
                pageTopPadding = look.topPadding,
                pageBottomPadding = look.bottomPadding,
                highlightRange = activeHighlight?.takeIf { it.chapterIndex == page.chapter.chapterIndex }?.let {
                    ReaderTtsCharacterRange(it.startCharOffset, it.endCharOffset)
                },
                onTapText = null,
                onLongPressCharOffset = if (enableLongPress && index == pager.currentPage && !dismissChrome) {
                    { offset -> restartCallback(page.chapter.chapterIndex, offset) }
                } else null,
                modifier = Modifier.fillMaxSize().onPlaced {
                    if (display.window == window && page.id !in display.placedPages) {
                        display.placedPages = display.placedPages + page.id
                    }
                },
            )
        }
    }
}

/** Text may consume down/up for long-press detection; movement and elapsed time decide short taps. */
internal fun Modifier.readerBodyTapInput(onTap: (Offset, IntSize) -> Unit): Modifier = composed {
    val latestTap by rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var moved = false
            var multiplePointers = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                multiplePointers = multiplePointers || event.changes.any { it.id != down.id && it.pressed }
                moved = moved || (change.isConsumed && change.position != change.previousPosition) ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                if (!change.pressed) {
                    if (!moved && !multiplePointers && change.previousPressed &&
                        change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                    ) {
                        latestTap(change.position, size)
                    }
                    break
                }
            }
        }
    }
}

private data class PageAppearance(
    val layout: ReaderPageLayoutKey,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val paragraphSpacing: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
)

private data class ChapterPages(val chapterIndex: Int, val layout: ReaderPageLayoutKey, val pages: List<ReaderPageSlice>)

private data class WindowPage(val chapter: ChapterPages, val pageIndex: Int) {
    val slice get() = chapter.pages[pageIndex]
    val id get() = "${chapter.chapterIndex}:${slice.startCharOffset}:${slice.endCharOffset}"
}

private data class ChapterPageWindow(
    val center: ChapterPages,
    val previous: ChapterPages?,
    val next: ChapterPages?,
    val appearance: PageAppearance,
) {
    val pages = buildList {
        previous?.let { add(WindowPage(it, it.pages.lastIndex)) }
        center.pages.indices.forEach { add(WindowPage(center, it)) }
        next?.let { add(WindowPage(it, 0)) }
    }
}

private data class PageGesture(
    val id: Long,
    val window: ChapterPageWindow,
    val startingPage: Int,
    val released: Boolean = false,
    val flingFinished: Boolean = false,
)

private class PageWindowDisplay {
    var window by mutableStateOf<ChapterPageWindow?>(null)
    var placedPages by mutableStateOf<Set<String>>(emptySet())
    var gesture by mutableStateOf<PageGesture?>(null)
    var nextGestureId = 0L
    val userGesture get() = gesture != null
    var confirmedWindow: ChapterPageWindow? = null
    var confirmedPageId: String? = null
}

private data class ResolvedPage(val page: WindowPage, val anchor: ReadingAnchor)

/** One computation at a time; each worker owns its measurer and its mutable layout cache. */
private class ChapterPageCalculations(
    private val bookId: String,
    private val repository: BookRepository,
    private val fontResolver: FontFamily.Resolver,
    private val layoutDirection: LayoutDirection,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Pair<Int, ReaderPageLayoutKey>, ChapterPages>()

    fun cached(chapter: Int, layout: ReaderPageLayoutKey) = cache[chapter to layout]

    fun retain(chapters: List<Int>, chapter: Int, layouts: Set<ReaderPageLayoutKey>) {
        val index = chapters.indexOf(chapter)
        val retained = listOfNotNull(chapters.getOrNull(index - 1), chapters.getOrNull(index), chapters.getOrNull(index + 1))
        cache.keys.removeAll { it.first !in retained || it.second !in layouts }
    }

    suspend fun pages(chapter: Int, layout: ReaderPageLayoutKey): ChapterPages = mutex.withLock {
        currentCoroutineContext().ensureActive()
        cached(chapter, layout)?.let { return@withLock it }
        val content = repository.getChapterText(bookId, chapter) ?: error("无法读取第 ${chapter + 1} 章正文。")
        val result = withContext(Dispatchers.Default) {
            Trace.beginSection("Reader.paginate:$chapter:${layout.fontSizeSp}:${content.length}")
            try {
                val measurer = TextMeasurer(
                    defaultFontFamilyResolver = fontResolver,
                    defaultDensity = Density(layout.density, layout.fontScale),
                    defaultLayoutDirection = layoutDirection,
                )
                paginatePageSlices(
                    content = content,
                    availableWidthPx = layout.widthPx,
                    availableHeightPx = layout.heightPx,
                    paragraphSpacingPx = layout.paragraphSpacingPx,
                    textMeasurer = measurer,
                    textStyle = TextStyle(
                        fontSize = layout.fontSizeSp.sp,
                        lineHeight = (layout.fontSizeSp * layout.lineHeightMultiplier).sp,
                        textAlign = TextAlign.Start,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    cancellationContext = currentCoroutineContext(),
                )
            } finally {
                Trace.endSection()
            }
        }
        currentCoroutineContext().ensureActive()
        ChapterPages(chapter, layout, result).also { cache[chapter to layout] = it }
    }

    suspend fun resolve(request: ReaderPositionRequest, layout: ReaderPageLayoutKey, chapters: List<Int>): ResolvedPage {
        require(request.anchor.chapterIndex in chapters) { "章节不存在。" }
        val neededLayouts = request.turns.map { it.layout }.toSet() + layout
        var anchor = request.anchor
        if (request.lastPage) {
            val initial = pages(anchor.chapterIndex, request.turns.firstOrNull()?.layout ?: layout)
            anchor = ReadingAnchor(anchor.chapterIndex, initial.pages.last().visibleStartCharOffset)
        }
        for (turn in request.turns) {
            currentCoroutineContext().ensureActive()
            val current = pages(anchor.chapterIndex, turn.layout)
            val targetIndex = ReaderPageAnchorMapper.pageIndexForCharOffset(current.pages, anchor.charOffset) + turn.direction
            if (targetIndex in current.pages.indices) {
                anchor = ReadingAnchor(current.chapterIndex, current.pages[targetIndex].visibleStartCharOffset)
            } else {
                val neighbor = chapters.getOrNull(chapters.indexOf(current.chapterIndex) + turn.direction)
                if (neighbor != null) {
                    retain(chapters, neighbor, neededLayouts)
                    val target = pages(neighbor, turn.layout)
                    val page = if (turn.direction > 0) target.pages.first() else target.pages.last()
                    anchor = ReadingAnchor(neighbor, page.visibleStartCharOffset)
                }
            }
        }
        val target = pages(anchor.chapterIndex, layout)
        val page = WindowPage(target, ReaderPageAnchorMapper.pageIndexForCharOffset(target.pages, anchor.charOffset))
        val raw = page.slice.rawText
        val local = (anchor.charOffset - page.slice.startCharOffset).coerceIn(0, raw.length)
        val visible = (local until raw.length).firstOrNull { !raw[it].isWhitespace() }
            ?: raw.indexOfLast { !it.isWhitespace() }.coerceAtLeast(0)
        return ResolvedPage(page, ReadingAnchor(anchor.chapterIndex, page.slice.startCharOffset + visible))
    }
}
