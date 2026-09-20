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
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
    pageState: ReaderPageState = rememberReaderPageState(bookId, repository),
    executeRequests: Boolean = true,
    onAnchorResolved: ((Long, ReadingAnchor) -> Unit)? = null,
) {
    val calculations = pageState.calculations
    val display = pageState.display
    val executing by rememberUpdatedState(executeRequests)
    val anchorCallback by rememberUpdatedState(onAnchorResolved)
    val layoutDirection = LocalLayoutDirection.current
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
        val request = position.request
        // A mode handoff can prepare PAGE without mounting a second reader. Its first composition
        // starts at the prepared page; placement still acknowledges the request below.
        val initialPage = remember(pageState) {
            val prepared = pageState.prepared?.takeIf { it.requestId == request?.id && it.layout == layout }
            if (prepared != null) {
                display.window = calculations.window(prepared.result.page.chapter, appearance, chapterIndices)
                display.placedPages = emptySet()
                pageState.prepared = null
                display.window!!.pages.indexOfFirst { it.id == prepared.result.page.id }.coerceAtLeast(0)
            } else {
                display.window?.pages?.indexOfFirst { it.id == display.confirmedPageId }?.coerceAtLeast(0) ?: 0
            }
        }
        val pager = rememberPagerState(initialPage = initialPage) { display.window?.pages?.size ?: 0 }
        SideEffect { if (executeRequests) position.notePageLayout(layout) }

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

        fun centeredWindow(batch: ChapterPages, look: PageAppearance): ChapterPageWindow =
            calculations.window(batch, look, chapterIndices)

        fun snapTargetIndex(): Int {
            val window = display.window ?: return 0
            val pending = position.request
            val resolved = pending?.let { calculations.resolveCached(it, window.appearance.layout, chapterIndices) }
            val desiredIds = listOfNotNull(
                resolved?.page?.id,
                display.acceptedPageId?.takeIf { display.acceptedEpoch == position.navigationEpoch },
                display.gesture?.sourcePageId,
                display.confirmedPageId,
            )
            return desiredIds.firstNotNullOfOrNull { id ->
                window.pages.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            } ?: pager.currentPage.coerceIn(window.pages.indices)
        }

        fun acceptTurn(direction: Int, sourceLayout: ReaderPageLayoutKey, epoch: Long? = null) {
            val accepted = position.turn(direction, sourceLayout, epoch) ?: return
            calculations.resolveCached(accepted, display.window?.appearance?.layout ?: layout, chapterIndices)?.let {
                if (display.window?.pages?.any { candidate -> candidate.id == it.page.id } == true) {
                    display.acceptedPageId = it.page.id
                    display.acceptedEpoch = accepted.navigationEpoch
                }
            }
        }

        fun recordPage(page: WindowPage, requestId: Long? = null, targetAnchor: ReadingAnchor? = null) {
            if (!executing || anchorCallback != null) return
            val previousChapter = position.confirmedAnchor.chapterIndex
            // Only accepted page turns are manual navigation. Following speech, opening its
            // notification or resuming listening also crosses chapters, without stopping audio.
            val wasManualTurn = requestId != null && position.request
                ?.takeIf { it.id == requestId }?.turns?.isNotEmpty() == true
            val anchor = targetAnchor ?: ReadingAnchor(page.chapter.chapterIndex, page.slice.visibleStartCharOffset)
            val accepted = if (requestId != null) position.confirm(requestId, anchor) else position.recordViewport(anchor)
            if (!accepted) return
            display.window?.let { window ->
                installWindow(centeredWindow(page.chapter, window.appearance), page)
            }
            display.confirmedWindow = display.window
            display.confirmedPageId = page.id
            calculations.retain(chapterIndices, page.chapter.chapterIndex, setOf(page.chapter.layout))
            if (wasManualTurn && previousChapter != anchor.chapterIndex) crossChapterCallback()
            confirmedCallback(anchor.chapterIndex, page.chapter.pages, page.pageIndex)
        }

        LaunchedEffect(layout, executeRequests) {
            if (executing && anchorCallback == null && position.error == null && display.window != null && display.window?.appearance?.layout != layout && position.request == null) {
                position.reflow()
            }
        }

        LaunchedEffect(position.error, position.request?.id, appearance) {
            val failure = position.error
            if (failure != null && position.request == null) {
                if (!position.hasConfirmedLayout) return@LaunchedEffect
                val anchor = position.confirmedAnchor
                val previous = display.confirmedWindow
                val page = previous?.pages?.firstOrNull { it.id == display.confirmedPageId }
                    ?.takeIf { it.chapter.chapterIndex == anchor.chapterIndex &&
                        anchor.charOffset in it.slice.startCharOffset until it.slice.endCharOffset }
                if (previous != null && page != null && previous.appearance.matchesViewport(appearance)) {
                    installWindow(previous, page)
                    pager.requestScrollToPage(previous.pages.indexOf(page))
                } else if (layout.widthPx > 0 && layout.heightPx > 0) {
                    try {
                        val restored = calculations.resolve(ReaderPositionRequest(-1, anchor), layout, chapterIndices)
                        if (position.request == null && position.error == failure && position.confirmedAnchor == anchor) {
                            installWindow(centeredWindow(restored.page.chapter, appearance), restored.page)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The original failure and its retry target remain authoritative. Do not
                        // loop, confirm fallback progress or apply old pixels to the new viewport.
                    }
                }
            }
        }

        LaunchedEffect(request?.id, layout, chapterIndices, calculations, executeRequests, onAnchorResolved != null) {
            if (!executing) return@LaunchedEffect
            val intent = request ?: return@LaunchedEffect
            if (layout.widthPx <= 0 || layout.heightPx <= 0) return@LaunchedEffect
            try {
                calculations.retain(chapterIndices, intent.anchor.chapterIndex, intent.turns.map { it.layout }.toSet() + layout)
                if (anchorCallback != null) {
                    val anchor = calculations.resolveAnchor(intent, layout, chapterIndices)
                    if (executing && position.request?.id == intent.id) anchorCallback?.invoke(intent.id, anchor)
                    return@LaunchedEffect
                }
                val resolved = calculations.resolve(intent, layout, chapterIndices)
                val target = resolved.page
                while (true) {
                    try {
                        snapshotFlow { !display.userGesture && !pager.isScrollInProgress }.first { it }
                        if (!executing || anchorCallback != null || position.request?.id != intent.id) return@LaunchedEffect
                        val oldWindow = display.window
                        val existingIndex = oldWindow?.takeIf { it.appearance.layout == layout }
                            ?.pages?.indexOfFirst { it.id == target.id } ?: -1
                        if (existingIndex >= 0) {
                            if (pager.settledPage != existingIndex) pager.animateScrollToPage(existingIndex)
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
                        break
                    } catch (interrupted: CancellationException) {
                        // A new finger may interrupt Pager's scroll mutation without revoking the
                        // request. Wait for that gesture and resume its accepted target; an actual
                        // request/effect cancellation still propagates immediately.
                        currentCoroutineContext().ensureActive()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (executing) position.fail(intent.id, failure.message ?: "当前章节排版失败，请重试。")
            }
        }

        // Optional neighbors follow the visible target; a new request cancels this work first.
        LaunchedEffect(display.window?.center, position.request?.id, calculations, executeRequests, onAnchorResolved != null) {
            if (!executing || anchorCallback != null || position.request != null || position.error != null) return@LaunchedEffect
            val initial = display.window ?: return@LaunchedEffect
            val at = chapterIndices.indexOf(initial.center.chapterIndex)
            for (neighbor in listOfNotNull(chapterIndices.getOrNull(at + 1), chapterIndices.getOrNull(at - 1))) {
                try {
                    calculations.pages(neighbor, initial.appearance.layout)
                    snapshotFlow { !display.userGesture && !pager.isScrollInProgress }.first { it }
                    val current = display.window ?: return@LaunchedEffect
                    if (!executing || anchorCallback != null || position.request != null || current.center != initial.center) return@LaunchedEffect
                    val visible = current.pages.getOrNull(pager.settledPage) ?: return@LaunchedEffect
                    installWindow(centeredWindow(current.center, current.appearance), visible)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the readable page. Explicit navigation retries and reports a target failure.
                }
            }
        }

        LaunchedEffect(followTargetCharOffset, activeHighlight?.chapterIndex, position.request?.id, executeRequests) {
            if (!executing || anchorCallback != null) return@LaunchedEffect
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

        val currentSnapTarget by rememberUpdatedState(newValue = { snapTargetIndex() })
        val snapProvider = remember(pager) {
            object : SnapLayoutInfoProvider {
                override fun calculateApproachOffset(velocity: Float, decayOffset: Float) = 0f
                override fun calculateSnapOffset(velocity: Float): Float =
                    pager.getOffsetDistanceInPages(currentSnapTarget()) * pager.layoutInfo.pageSize
            }
        }
        val nativeSnap = rememberSnapFlingBehavior(snapProvider)
        val flingBehavior = remember(nativeSnap, display) {
            object : TargetedFlingBehavior {
                override suspend fun ScrollScope.performFling(
                    initialVelocity: Float,
                    onRemainingDistanceUpdated: (Float) -> Unit,
                ): Float {
                    val owner = display.gesture?.takeIf { it.released }?.id
                    try {
                        return with(nativeSnap) { performFling(initialVelocity, onRemainingDistanceUpdated) }
                    } finally {
                        // Fling owns animation only. An interrupted animation never retracts input.
                        display.gesture?.takeIf { it.id == owner }?.let {
                            display.gesture = it.copy(flingFinished = true)
                        }
                    }
                }
            }
        }
        LaunchedEffect(pager, display) {
            pager.interactionSource.interactions.collect { interaction ->
                val current = display.gesture
                when (interaction) {
                    is DragInteraction.Start -> if (current != null) {
                        display.gesture = current.copy(nativeDrag = interaction)
                    }
                    is DragInteraction.Cancel -> if (current?.nativeDrag == interaction.start) {
                        display.gesture = null
                    }
                    else -> Unit
                }
            }
        }
        val gesture = display.gesture
        LaunchedEffect(gesture?.id, gesture?.released, gesture?.flingFinished, executeRequests) {
            if (!executing) {
                display.gesture = null
                return@LaunchedEffect
            }
            val finished = gesture?.takeIf { it.released && it.flingFinished } ?: return@LaunchedEffect
            snapshotFlow { !pager.isScrollInProgress }.first { it }
            if (display.gesture?.id == finished.id) display.gesture = null
        }

        HorizontalPager(
            state = pager,
            flingBehavior = flingBehavior,
            userScrollEnabled = executeRequests && onAnchorResolved == null,
            key = { index -> checkNotNull(display.window).pages[index].id },
            modifier = Modifier.fillMaxSize().readerPageGestureInput(
                enabled = executeRequests && onAnchorResolved == null,
                longPressEnabled = enableLongPress && !dismissChrome,
                forwardSign = if (layoutDirection == LayoutDirection.Ltr) -1f else 1f,
                density = density.density,
                onStart = {
                    val window = display.window
                    if (window == null) null else {
                        val id = display.nextGestureId++
                        display.gesture = PageGesture(id, window.appearance.layout,
                            window.pages.getOrNull(pager.settledPage)?.id, position.navigationEpoch)
                        id
                    }
                },
                onDrag = { owner ->
                    if (display.gesture?.id == owner) interruptCallback()
                },
                onRelease = { owner, direction ->
                    display.gesture?.takeIf { it.id == owner }?.let { input ->
                        if (direction != 0) acceptTurn(direction, input.layout, input.navigationEpoch)
                        display.gesture = input.copy(released = true, flingFinished = input.nativeDrag == null)
                    }
                },
                onCancel = { owner -> if (display.gesture?.id == owner) display.gesture = null },
            ).readerBodyTapInput { offset, size ->
                val zone = ReaderTapZone.resolve(offset.x, size.width.toFloat())
                if (dismissChrome || zone == ReaderTapZone.TOGGLE_CHROME) {
                    toggleCallback()
                } else if (executing && anchorCallback == null) {
                    when (zone) {
                        ReaderTapZone.TOGGLE_CHROME -> toggleCallback()
                        ReaderTapZone.PREVIOUS -> {
                            interruptCallback()
                            acceptTurn(-1, display.window?.appearance?.layout ?: layout)
                        }
                        ReaderTapZone.NEXT -> {
                            interruptCallback()
                            acceptTurn(1, display.window?.appearance?.layout ?: layout)
                        }
                    }
                }
            },
        ) { index ->
            val window = display.window ?: return@HorizontalPager
            val page = window.pages.getOrNull(index) ?: return@HorizontalPager
            val look = window.appearance
            // A stale pixel layout cannot be painted into a changed viewport while restoring.
            if (!look.matchesViewport(appearance)) return@HorizontalPager
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
                onLongPressCharOffset = if (executeRequests && onAnchorResolved == null && enableLongPress && index == pager.currentPage && !dismissChrome) {
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

internal data class PageAppearance(
    val layout: ReaderPageLayoutKey,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val paragraphSpacing: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
) {
    fun matchesViewport(other: PageAppearance): Boolean =
        layout.widthPx == other.layout.widthPx && layout.heightPx == other.layout.heightPx &&
            layout.density == other.layout.density && layout.fontScale == other.layout.fontScale &&
            topPadding == other.topPadding && bottomPadding == other.bottomPadding
}

internal data class ChapterPages(val chapterIndex: Int, val layout: ReaderPageLayoutKey, val pages: List<ReaderPageSlice>)

internal data class WindowPage(val chapter: ChapterPages, val pageIndex: Int) {
    val slice get() = chapter.pages[pageIndex]
    val id get() = "${chapter.chapterIndex}:${slice.startCharOffset}:${slice.endCharOffset}"
}

internal data class ChapterPageWindow(
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

internal data class PageGesture(
    val id: Long,
    val layout: ReaderPageLayoutKey,
    val sourcePageId: String?,
    val navigationEpoch: Long,
    val nativeDrag: DragInteraction.Start? = null,
    val released: Boolean = false,
    val flingFinished: Boolean = false,
)

internal class PageWindowDisplay {
    var window by mutableStateOf<ChapterPageWindow?>(null)
    var placedPages by mutableStateOf<Set<String>>(emptySet())
    var gesture by mutableStateOf<PageGesture?>(null)
    var nextGestureId = 0L
    val userGesture get() = gesture != null
    var confirmedWindow: ChapterPageWindow? = null
    var confirmedPageId: String? = null
    var acceptedPageId: String? = null
    var acceptedEpoch: Long = -1
}

internal data class ResolvedPage(val page: WindowPage, val anchor: ReadingAnchor)
internal data class PreparedReaderPage(val requestId: Long, val layout: ReaderPageLayoutKey, val result: ResolvedPage)

internal class ReaderPageState internal constructor(internal val calculations: ChapterPageCalculations) {
    internal val display = PageWindowDisplay()
    internal var prepared: PreparedReaderPage? = null

    suspend fun prepare(request: ReaderPositionRequest, layout: ReaderPageLayoutKey, chapters: List<Int>): ReadingAnchor {
        val resolved = calculations.resolve(request, layout, chapters)
        currentCoroutineContext().ensureActive()
        prepared = PreparedReaderPage(request.id, layout, resolved)
        return resolved.anchor
    }
}

@Composable
internal fun rememberReaderPageState(bookId: String, repository: BookRepository): ReaderPageState {
    val resolver = LocalFontFamilyResolver.current
    val direction = LocalLayoutDirection.current
    return remember(bookId, repository, resolver, direction) {
        ReaderPageState(ChapterPageCalculations(bookId, repository, resolver, direction))
    }
}

/** One computation at a time; each worker owns its measurer and its mutable layout cache. */
internal class ChapterPageCalculations(
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

    suspend fun pages(chapter: Int, layout: ReaderPageLayoutKey): ChapterPages {
        currentCoroutineContext().ensureActive()
        cached(chapter, layout)?.let { return it }
        return mutex.withLock {
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
    }

    fun window(batch: ChapterPages, appearance: PageAppearance, chapters: List<Int>): ChapterPageWindow {
        val at = chapters.indexOf(batch.chapterIndex)
        return ChapterPageWindow(batch,
            chapters.getOrNull(at - 1)?.let { cached(it, appearance.layout) },
            chapters.getOrNull(at + 1)?.let { cached(it, appearance.layout) }, appearance)
    }

    suspend fun resolveAnchor(request: ReaderPositionRequest, layout: ReaderPageLayoutKey, chapters: List<Int>): ReadingAnchor =
        resolveNavigation(request, layout, chapters) { chapter, key ->
            currentCoroutineContext().ensureActive()
            pages(chapter, key)
        }

    suspend fun resolve(request: ReaderPositionRequest, layout: ReaderPageLayoutKey, chapters: List<Int>): ResolvedPage {
        val anchor = resolveAnchor(request, layout, chapters)
        return resolvePage(anchor, pages(anchor.chapterIndex, layout))
    }

    fun resolveCached(request: ReaderPositionRequest, layout: ReaderPageLayoutKey, chapters: List<Int>): ResolvedPage? {
        if (request.anchor.chapterIndex !in chapters) return null
        val anchor = resolveNavigation(request, layout, chapters) { chapter, key ->
            cached(chapter, key) ?: return null
        }
        return resolvePage(anchor, cached(anchor.chapterIndex, layout) ?: return null)
    }

    /** Inline loading keeps cached-only and suspending navigation on exactly the same algorithm. */
    private inline fun resolveNavigation(
        request: ReaderPositionRequest,
        layout: ReaderPageLayoutKey,
        chapters: List<Int>,
        load: (Int, ReaderPageLayoutKey) -> ChapterPages,
    ): ReadingAnchor {
        require(request.anchor.chapterIndex in chapters) { "章节不存在。" }
        var anchor = request.anchor
        if (request.lastPage) {
            val initial = load(anchor.chapterIndex, request.lastPageLayout ?: request.turns.firstOrNull()?.layout ?: layout)
            anchor = ReadingAnchor(anchor.chapterIndex, initial.pages.last().visibleStartCharOffset)
        }
        for (turn in request.turns) {
            val current = load(anchor.chapterIndex, turn.layout)
            val targetIndex = ReaderPageAnchorMapper.pageIndexForCharOffset(current.pages, anchor.charOffset) + turn.direction
            if (targetIndex in current.pages.indices) {
                anchor = ReadingAnchor(current.chapterIndex, current.pages[targetIndex].visibleStartCharOffset)
            } else {
                val neighbor = chapters.getOrNull(chapters.indexOf(current.chapterIndex) + turn.direction)
                if (neighbor != null) {
                    val target = load(neighbor, turn.layout)
                    val page = if (turn.direction > 0) target.pages.first() else target.pages.last()
                    anchor = ReadingAnchor(neighbor, page.visibleStartCharOffset)
                }
            }
        }
        return anchor
    }

    private fun resolvePage(anchor: ReadingAnchor, target: ChapterPages): ResolvedPage {
        val page = WindowPage(target, ReaderPageAnchorMapper.pageIndexForCharOffset(target.pages, anchor.charOffset))
        val raw = page.slice.rawText
        val local = (anchor.charOffset - page.slice.startCharOffset).coerceIn(0, raw.length)
        val visible = (local until raw.length).firstOrNull { !raw[it].isWhitespace() }
            ?: raw.indexOfLast { !it.isWhitespace() }.coerceAtLeast(0)
        return ResolvedPage(page, ReadingAnchor(anchor.chapterIndex, page.slice.startCharOffset + visible))
    }
}
