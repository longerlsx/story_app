package com.longerlsx.storyapp.feature.reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.Constraints
import androidx.core.view.WindowCompat
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.data.book.BookRepository
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import com.longerlsx.storyapp.feature.reader.tts.ReaderTextStartLocation
import com.longerlsx.storyapp.feature.reader.tts.ReaderTextStartLocator
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsActiveVisualRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsController
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsFollowSuppressionPolicy
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsStartRequest
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState
import com.longerlsx.storyapp.feature.reader.tts.isOngoingSession
import com.longerlsx.storyapp.feature.reader.tts.restartVisualRangeOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt

internal val ReaderHorizontalPadding = 24.dp
private val ReaderScrollViewportTopInset = 52.dp
private val ReaderScrollReadableTopSpacing = 8.dp
private val ReaderTopPadding = 12.dp
private val ReaderBottomPadding = 96.dp
internal val ReaderPageTopPadding = 52.dp
internal val ReaderPageBottomPadding = 56.dp

@Composable
fun ReaderScreen(
    bookId: String,
    repository: BookRepository,
    settingsStore: ReaderSettingsStore,
    ttsController: ReaderTtsController,
    onBack: () -> Unit,
    listeningOpenRequest: ReaderTtsStartRequest? = null,
    onListeningOpenHandled: () -> Unit = {},
) {
    val state = produceState<ReaderScreenState>(
        initialValue = ReaderScreenState.Loading,
        key1 = bookId,
    ) {
        val book = repository.getBook(bookId)
        val chapters = repository.getChapters(bookId)
        val progress = repository.getReadingProgress(bookId)
        val chapterIndex = OpeningChapterSelector.select(
            chapters = chapters,
            progress = progress,
        )
        value = if (book == null) {
            ReaderScreenState.NotFound
        } else {
            ReaderScreenState.Ready(
                book = book,
                chapters = chapters,
                initialChapterIndex = chapterIndex,
                initialCharOffset = progress?.anchor
                    ?.takeIf { it.chapterIndex == chapterIndex }
                    ?.charOffset
                    ?: 0,
            )
        }
    }.value

    when (state) {
        ReaderScreenState.Loading -> ReaderLoadingContent(padding = PaddingValues(0.dp))
        ReaderScreenState.NotFound -> ReaderNotFoundContent(
            padding = PaddingValues(0.dp),
            onBack = onBack,
        )

        is ReaderScreenState.Ready -> ReaderReadyContent(
            padding = PaddingValues(0.dp),
            state = state,
            repository = repository,
            settingsStore = settingsStore,
            ttsController = ttsController,
            onBack = onBack,
            listeningOpenRequest = listeningOpenRequest,
            onListeningOpenHandled = onListeningOpenHandled,
        )
    }
}

@Composable
private fun ReaderLoadingContent(
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "正在打开书籍…")
    }
}

@Composable
private fun ReaderNotFoundContent(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "没有找到这本书。")
        Button(
            onClick = onBack,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Text(text = "返回书架")
        }
    }
}

@Composable
private fun ReaderReadyContent(
    padding: PaddingValues,
    state: ReaderScreenState.Ready,
    repository: BookRepository,
    settingsStore: ReaderSettingsStore,
    ttsController: ReaderTtsController,
    onBack: () -> Unit,
    listeningOpenRequest: ReaderTtsStartRequest?,
    onListeningOpenHandled: () -> Unit,
) {
    val context = LocalContext.current
    val rootDensity = LocalDensity.current
    val storyApplication = remember(context) {
        context.applicationContext as StoryApplication
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val position = remember(state.book.id) {
        ReaderPositionState(ReadingAnchor(state.initialChapterIndex, state.initialCharOffset))
    }
    val selectedChapterIndex = position.confirmedAnchor.chapterIndex
    var chromeMode by remember(state.book.id) {
        mutableStateOf(ReaderChromeMode.READING_ONLY)
    }
    var lastChromeInteractionAtMs by remember(state.book.id) {
        mutableStateOf<Long?>(null)
    }
    var selectedSettings by remember(state.book.id) { mutableStateOf(settingsStore.load()) }
    var readerSettings by remember(state.book.id) { mutableStateOf(selectedSettings) }
    val pageState = rememberReaderPageState(state.book.id, repository)
    var confirmedMode by remember(state.book.id) { mutableStateOf(selectedSettings.readingMode) }
    var confirmedDisplaySettings by remember(state.book.id) { mutableStateOf(selectedSettings) }
    var confirmedScrollViewport by remember(state.book.id) { mutableStateOf<ReaderScrollSnapshot?>(null) }
    var preparedPageRequestId by remember(state.book.id) { mutableStateOf<Long?>(null) }
    var pendingChromeAutoHideRefreshAfterRestore by remember(state.book.id) {
        mutableStateOf(false)
    }
    val restoredPosition = position.hasConfirmedLayout && position.request == null && position.error == null
    val requestedSettings = selectedSettings
    var contentRetry by remember(state.book.id) { mutableIntStateOf(0) }
    var viewportSize by remember(state.book.id) { mutableStateOf(IntSize.Zero) }
    var pendingNotificationPermissionStartRequest by remember(state.book.id) {
        mutableStateOf<ReaderTtsStartRequest?>(null)
    }
    var lastManualFollowInterruptionAtMs by remember(state.book.id) {
        mutableStateOf<Long?>(null)
    }
    var pendingRestartVisualRange by remember(state.book.id) {
        mutableStateOf<ReaderTtsActiveVisualRange?>(null)
    }
    var localListeningError by remember(state.book.id) {
        mutableStateOf<String?>(null)
    }
    var lastListeningStartRequest by remember(state.book.id) {
        mutableStateOf<ReaderTtsStartRequest?>(null)
    }
    var isProgrammaticScrollFollowInFlight by remember(state.book.id) {
        mutableStateOf(false)
    }
    val ttsRuntime by ttsController.runtimeState.collectAsState()
    var readerForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    LaunchedEffect(state.book.id, listeningOpenRequest) {
        val request = listeningOpenRequest?.takeIf { it.bookId == state.book.id } ?: return@LaunchedEffect
        if (state.chapters.any { it.chapterIndex == request.chapterIndex }) {
            lastManualFollowInterruptionAtMs = null
            position.jump(ReadingAnchor(request.chapterIndex, request.charOffset))
            chromeMode = ReaderChromeMode.READING_ONLY
        } else {
            localListeningError = "听书章节已不存在，请从目录选择正文。"
            chromeMode = ReaderChromeMode.LISTENING_EXPANDED
        }
        onListeningOpenHandled()
    }

    val selectedChapterPosition = state.chapters.indexOfFirst { it.chapterIndex == selectedChapterIndex }
    val selectedChapter = state.chapters.firstOrNull { it.chapterIndex == selectedChapterIndex }
    val feedKey = ReaderFeedKey(state.book.id, readerSettings.readingMode,
        selectedChapterIndex.takeIf { readerSettings.readingMode == ReadingMode.PAGE }, contentRetry)
    val feedResult by produceState<ReaderFeedResult>(ReaderFeedResult.Loading(feedKey), feedKey) {
        value = ReaderFeedResult.Loading(feedKey)
        try {
            val needed = if (feedKey.mode == ReadingMode.SCROLL) state.chapters
                else state.chapters.filter { it.chapterIndex == feedKey.chapterIndex }
            val content = needed.map { chapter ->
                ReaderFeedChapterContent(chapter, repository.getChapterText(feedKey.bookId, chapter.chapterIndex)
                    ?: error("无法读取第 ${chapter.chapterIndex + 1} 章正文。"))
            }
            value = ReaderFeedResult.Ready(feedKey, content)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            value = ReaderFeedResult.Failed(feedKey, failure.message ?: "正文读取失败")
        }
    }
    val readyFeed = (feedResult as? ReaderFeedResult.Ready)?.takeIf { it.key == feedKey }
    val waitingForModeData = position.hasConfirmedLayout && when (readerSettings.readingMode) {
        ReadingMode.SCROLL -> readyFeed == null
        ReadingMode.PAGE -> confirmedMode == ReadingMode.SCROLL && preparedPageRequestId != position.request?.id
    }
    val displayMode = if (position.hasConfirmedLayout && (position.error != null || waitingForModeData))
        confirmedMode else readerSettings.readingMode
    val displaySettings = when {
        position.error != null -> selectedSettings.copy(readingMode = displayMode)
        displayMode != readerSettings.readingMode -> confirmedDisplaySettings
        else -> readerSettings
    }
    val scrollFeed = if (displayMode == ReadingMode.SCROLL) {
        if (position.error == null && readyFeed?.key?.mode == ReadingMode.SCROLL) readyFeed.content
        else confirmedScrollViewport?.content.orEmpty()
    } else readyFeed?.content.orEmpty()
    val canLocateScroll = readerSettings.readingMode == ReadingMode.SCROLL && displayMode == ReadingMode.SCROLL &&
        readyFeed?.key?.mode == ReadingMode.SCROLL && position.error == null
    // Text availability is not a display acknowledgement. The real list lays out behind the
    // last confirmed page until the current request has reached its target line.
    val retainPageUntilScrollConfirmed = displayMode == ReadingMode.SCROLL && confirmedMode == ReadingMode.PAGE &&
        position.request != null && position.error == null

    LaunchedEffect(feedKey, feedResult, position.request?.id) {
        val failed = (feedResult as? ReaderFeedResult.Failed)?.takeIf { it.key == feedKey } ?: return@LaunchedEffect
        if (readerSettings.readingMode == ReadingMode.SCROLL) {
            position.request?.let { position.fail(it.id, failed.message) }
        }
    }
    val chapterTextByIndex = remember(scrollFeed) {
        scrollFeed.associate { it.chapter.chapterIndex to it.text }
    }
    val initialScrollItemIndex = remember(state.book.id, state.initialChapterIndex, state.chapters) {
        state.chapters.indexOfFirst { it.chapterIndex == state.initialChapterIndex }
            .takeIf { it >= 0 }
            ?: 0
    }
    val scrollLayoutKey = ReaderScrollLayoutKey(
        widthPx = viewportSize.width,
        density = rootDensity.density,
        fontScale = rootDensity.fontScale,
        fontSizeSp = displaySettings.fontSizeSp,
        lineHeightMultiplier = displaySettings.lineHeightMultiplier,
        paragraphSpacingEm = displaySettings.paragraphSpacingEm,
    )
    val scrollBodyMetricsByChapter = remember(
        state.book.id, scrollLayoutKey,
    ) {
        mutableStateMapOf<Int, ReaderScrollBodyMetrics>()
    }
    val onScrollBodyMetricsChanged = remember(state.book.id, scrollLayoutKey) {
        { chapterIndex: Int, metrics: ReaderScrollBodyMetrics ->
            scrollBodyMetricsByChapter[chapterIndex] = metrics
        }
    }
    val onScrollBodyMetricsDisposed: (Int) -> Unit = remember(state.book.id, scrollLayoutKey) {
        { chapterIndex -> scrollBodyMetricsByChapter.remove(chapterIndex) }
    }
    val scrollListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollItemIndex,
    )
    val themePalette = ReaderThemeResolver.resolveActivePalette(selectedSettings)
    val contentFontSize = displaySettings.fontSizeSp.sp
    val contentLineHeight = (displaySettings.fontSizeSp * displaySettings.lineHeightMultiplier).sp
    val paragraphSpacing = (displaySettings.fontSizeSp * displaySettings.paragraphSpacingEm).coerceIn(8f, 36f).dp
    // Controls overlay a fixed reading viewport; showing chrome is not navigation.
    val scrollReadableViewportTopPx = with(rootDensity) { ReaderScrollReadableTopSpacing.roundToPx() }
    val progressSummary = ReaderChapterProgressFormatter.format(
        chapters = state.chapters,
        selectedChapterPosition = selectedChapterPosition,
    )
    val activeBrightness = ReaderBrightnessResolver.resolveActiveBrightness(selectedSettings)
    val brightnessOverlayAlpha = ((1f - activeBrightness).coerceIn(0f, 1f) * 0.42f)

    ReaderSystemBarsEffect(themePalette = themePalette)

    fun visibleScrollAnchor(): ReadingAnchor? {
        val visibleItems = buildScrollVisibleChapterItems(
            listState = scrollListState,
            scrollFeed = scrollFeed,
            bodyMetricsByChapter = scrollBodyMetricsByChapter,
        )
        val activeChapterIndex = ReaderActiveChapterResolver.resolve(
            visibleItems = visibleItems,
            chapters = state.chapters,
            viewportTopPx = scrollReadableViewportTopPx,
        ) ?: return null
        val activeItem = visibleItems.firstOrNull { it.chapterIndex == activeChapterIndex } ?: return null
        val lines = scrollBodyMetricsByChapter[activeChapterIndex]?.lines?.takeIf { it.isNotEmpty() } ?: return null
        return ReadingAnchor(activeChapterIndex, ReaderLineAnchorMapper.charOffsetAtTop(
            lines, (scrollReadableViewportTopPx - activeItem.bodyOffsetPx).toFloat(),
        ))
    }

    fun rememberScrollViewport(anchor: ReadingAnchor) {
        confirmedMode = ReadingMode.SCROLL
        confirmedDisplaySettings = displaySettings
        confirmedScrollViewport = ReaderScrollSnapshot(state.book.id, scrollFeed, anchor,
            scrollLayoutKey, viewportSize, scrollListState.firstVisibleItemIndex,
            scrollListState.firstVisibleItemScrollOffset)
    }

    suspend fun scrollToAnchor(anchor: ReadingAnchor) {
        val targetIndex = scrollFeed.indexOfFirst { it.chapter.chapterIndex == anchor.chapterIndex }
        check(targetIndex >= 0) { "目标章节不存在" }
        snapshotFlow { scrollListState.layoutInfo.totalItemsCount }.first { it > targetIndex }
        if (scrollListState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
            scrollListState.scrollToItem(targetIndex)
        }
        val item = snapshotFlow {
            buildScrollVisibleChapterItems(scrollListState, scrollFeed, scrollBodyMetricsByChapter)
                .firstOrNull { it.itemIndex == targetIndex }
                ?.takeIf { scrollBodyMetricsByChapter[it.chapterIndex]?.lines?.isNotEmpty() == true }
        }.filterNotNull().first()
        val lines = scrollBodyMetricsByChapter.getValue(anchor.chapterIndex).lines
        val offset = (item.bodyOffsetWithinItemPx() +
            ReaderLineAnchorMapper.topForCharOffset(lines, anchor.charOffset).roundToInt() -
            scrollReadableViewportTopPx).coerceAtLeast(0)
        scrollListState.scrollToItem(targetIndex, offset)
        androidx.compose.runtime.withFrameNanos { }
    }

    fun captureProgress(): ReadingProgress? {
        if (!position.hasConfirmedLayout) return null
        if (canLocateScroll && confirmedMode == ReadingMode.SCROLL && position.request == null && position.error == null) {
            visibleScrollAnchor()?.let { anchor ->
                if (position.recordViewport(anchor)) rememberScrollViewport(anchor)
            }
        }
        return ReadingProgress(state.book.id, position.confirmedAnchor, confirmedMode, System.currentTimeMillis())
    }

    fun queueCurrentProgress() = captureProgress()?.let { storyApplication.persistReadingProgress(it, repository) }

    fun leaveReader() {
        val pendingSave = queueCurrentProgress()
        scope.launch {
            settingsStore.awaitPendingWrites()
            pendingSave?.join()
            onBack()
        }
    }

    fun openChapter(
        chapterIndex: Int,
        nextChromeMode: ReaderChromeMode,
        restoreCharOffset: Int = 0,
        restoreToLastPage: Boolean = false,
        refreshChromeAutoHide: Boolean = false,
    ) {
        position.jump(ReadingAnchor(chapterIndex, restoreCharOffset), lastPage = restoreToLastPage)
        chromeMode = nextChromeMode
        pendingChromeAutoHideRefreshAfterRestore =
            refreshChromeAutoHide && nextChromeMode == ReaderChromeMode.CHROME_VISIBLE
        lastChromeInteractionAtMs = when {
            nextChromeMode != ReaderChromeMode.CHROME_VISIBLE -> null
            refreshChromeAutoHide -> lastChromeInteractionAtMs
            else -> lastChromeInteractionAtMs
        }
    }

    fun adjacentChapter(delta: Int): Chapter? {
        val baseChapter = position.request?.anchor?.chapterIndex ?: selectedChapterIndex
        val baseIndex = state.chapters.indexOfFirst { it.chapterIndex == baseChapter }
        return if (baseIndex >= 0) state.chapters.getOrNull(baseIndex + delta) else null
    }

    fun openAdjacentChapter(
        delta: Int,
        nextChromeMode: ReaderChromeMode,
        refreshChromeAutoHide: Boolean = false,
    ) {
        val chapter = adjacentChapter(delta) ?: return
        openChapter(
            chapterIndex = chapter.chapterIndex,
            nextChromeMode = nextChromeMode,
            refreshChromeAutoHide = refreshChromeAutoHide,
        )
    }

    fun updateReaderSettings(requested: ReaderSettings) {
        if (requested == selectedSettings) return
        val previous = selectedSettings
        selectedSettings = requested
        settingsStore.save(requested)
        val needsLayout = requested.readingMode != previous.readingMode ||
            requested.fontSizeSp != previous.fontSizeSp ||
            requested.lineHeightMultiplier != previous.lineHeightMultiplier ||
            requested.paragraphSpacingEm != previous.paragraphSpacingEm
        if (needsLayout && position.error == null) {
            if (canLocateScroll && position.request == null) visibleScrollAnchor()?.let {
                if (position.recordViewport(it)) rememberScrollViewport(it)
            }
            position.reflow()
        }
        val needsPageAnchor = position.request?.let { it.turns.isNotEmpty() || it.lastPage } == true
        readerSettings = if (needsPageAnchor && requested.readingMode != ReadingMode.PAGE)
            requested.copy(readingMode = ReadingMode.PAGE) else requested
    }

    // Absolute navigation can replace unresolved page turns while a mode choice is pending.
    LaunchedEffect(position.request?.id, selectedSettings.readingMode) {
        if (position.error != null) return@LaunchedEffect
        val request = position.request
        val needsPageAnchor = request?.let { it.turns.isNotEmpty() || it.lastPage } == true
        val nextMode = if (needsPageAnchor) ReadingMode.PAGE else selectedSettings.readingMode
        if (readerSettings.readingMode != nextMode) {
            if (request == null) position.reflow() else position.handoff()
            readerSettings = selectedSettings.copy(readingMode = nextMode)
        }
    }

    LaunchedEffect(position.request?.id) {
        val request = position.request ?: return@LaunchedEffect
        var elapsed = 0L
        var previous = SystemClock.elapsedRealtime()
        while (elapsed < 10_000) {
            delay(100)
            val now = SystemClock.elapsedRealtime()
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) elapsed += now - previous
            previous = now
        }
        position.fail(request.id, "定位等待超时，可以重试或返回书架。")
    }

    BackHandler {
        if (chromeMode == ReaderChromeMode.DIRECTORY_OPEN || chromeMode == ReaderChromeMode.SETTINGS_EXPANDED ||
            chromeMode == ReaderChromeMode.LISTENING_EXPANDED) {
            chromeMode = ReaderChromeMode.CHROME_VISIBLE
        } else leaveReader()
    }

    fun updateTtsSettings(nextTtsSettings: ReaderTtsSettings) {
        if (nextTtsSettings == selectedSettings.ttsSettings) return
        updateReaderSettings(selectedSettings.copy(ttsSettings = nextTtsSettings))
        ttsController.applySettings(nextTtsSettings)
    }

    fun toggleAppearanceMode() {
        updateReaderSettings(
            requestedSettings.copy(
                appearanceMode = if (selectedSettings.appearanceMode == ReaderAppearanceMode.DAY) {
                    ReaderAppearanceMode.NIGHT
                } else {
                    ReaderAppearanceMode.DAY
                },
            ),
        )
    }

    fun setChromeMode(
        nextChromeMode: ReaderChromeMode,
        refreshAutoHide: Boolean = nextChromeMode == ReaderChromeMode.CHROME_VISIBLE,
    ) {
        chromeMode = nextChromeMode
        lastChromeInteractionAtMs = if (nextChromeMode == ReaderChromeMode.CHROME_VISIBLE && refreshAutoHide) {
            SystemClock.elapsedRealtime()
        } else {
            null
        }
    }

    fun bumpChromeInteraction() {
        if (chromeMode == ReaderChromeMode.CHROME_VISIBLE) {
            lastChromeInteractionAtMs = SystemClock.elapsedRealtime()
        }
    }

    fun displayedTopLine(): ReaderTextStartLocation? {
        if (!position.hasConfirmedLayout) return null
        if (displayMode == ReadingMode.PAGE || retainPageUntilScrollConfirmed) {
            // A restored anchor can be in the middle of its page. Use the displayed
            // page's actual first body line, including when another target is loading.
            val display = pageState.display
            val page = display.confirmedWindow?.pages?.firstOrNull { it.id == display.confirmedPageId }
                ?: return null
            return ReaderTextStartLocation(page.chapter.chapterIndex, page.slice.visibleStartCharOffset)
        }
        val visible = buildScrollVisibleChapterItems(scrollListState, scrollFeed, scrollBodyMetricsByChapter)
        val viewportEnd = scrollListState.layoutInfo.viewportEndOffset
        for (item in visible.sortedBy { it.offsetPx }) {
            val line = scrollBodyMetricsByChapter[item.chapterIndex]?.lines?.firstOrNull {
                item.bodyOffsetPx + it.bottomPx > scrollReadableViewportTopPx &&
                    item.bodyOffsetPx + it.topPx < viewportEnd
            } ?: continue
            return ReaderTextStartLocation(item.chapterIndex, line.startCharOffset)
        }
        return null
    }

    fun buildTtsStartRequest(explicitLocation: ReaderTextStartLocation): ReaderTtsStartRequest {
        return ReaderTtsStartRequestFactory.create(
            book = state.book,
            chapters = state.chapters,
            startLocation = explicitLocation,
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingNotificationPermissionStartRequest ?: return@rememberLauncherForActivityResult
        pendingNotificationPermissionStartRequest = null
        ttsController.start(
            request = request,
            settings = selectedSettings.ttsSettings,
            notificationControlsAvailable = granted,
        )
    }

    fun startTtsRequest(request: ReaderTtsStartRequest) {
        localListeningError = null
        lastListeningStartRequest = request
        if (ttsRuntime.currentBookId == state.book.id && ttsRuntime.playbackState.isOngoingSession()) {
            ttsController.restartFromLocation(request)
            return
        }
        val notificationsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        when (
            ReaderTtsNotificationPermissionPolicy.resolve(
                sdkInt = Build.VERSION.SDK_INT,
                notificationPermissionGranted = notificationsGranted,
            )
        ) {
            ReaderTtsNotificationPermissionDecision.RequestPermissionFirst -> {
                pendingNotificationPermissionStartRequest = request
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }

            ReaderTtsNotificationPermissionDecision.StartImmediately -> Unit
        }
        ttsController.start(
            request = request,
            settings = selectedSettings.ttsSettings,
            notificationControlsAvailable = true,
        )
    }

    fun startTtsFromCurrentLocation() {
        val location = displayedTopLine()
        if (location == null) {
            localListeningError = "正文还未显示完成，请稍后重试。"
            return
        }
        lastManualFollowInterruptionAtMs = null
        // Snapshot before opening an overlay or waiting for Android permission.
        startTtsRequest(buildTtsStartRequest(location))
    }

    fun retryListening() {
        val snapshot = ttsRuntime.playbackSnapshot
        val segment = snapshot.currentSegment
        val failedRequest = if (ttsRuntime.currentBookId == state.book.id &&
            ttsRuntime.playbackState == ReaderTtsSessionState.FAILED) {
            segment?.let {
                buildTtsStartRequest(ReaderTextStartLocation(it.chapterIndex,
                    snapshot.nextRecoverableCharOffset ?: it.startCharOffset))
            } ?: lastListeningStartRequest
        } else null
        if (failedRequest != null) startTtsRequest(failedRequest) else startTtsFromCurrentLocation()
    }

    val currentBookTtsSession = ReaderTtsCurrentBookSessionResolver.resolve(
        currentBookId = state.book.id,
        runtimeState = ttsRuntime,
    )

    fun restartTtsFromLocation(location: ReaderTextStartLocation) {
        if (!currentBookTtsSession.isOngoing) {
            return
        }
        pendingRestartVisualRange = location.restartVisualRangeOrNull(
            chapterText = chapterTextByIndex[location.chapterIndex].orEmpty(),
        )
        lastManualFollowInterruptionAtMs = null
        ttsController.restartFromLocation(
            buildTtsStartRequest(explicitLocation = location),
        )
    }

    fun restartTtsFromPressedOffset(
        chapterIndex: Int,
        pressedCharOffset: Int,
    ) {
        val location = ReaderTextStartLocator.resolveRestartLocation(
            chapterIndex = chapterIndex,
            text = chapterTextByIndex[chapterIndex].orEmpty(),
            pressedCharOffset = pressedCharOffset,
            nonBodyRanges = emptyList(),
        ) ?: return
        restartTtsFromLocation(location)
    }

    fun markManualFollowInterruption() {
        if (!currentBookTtsSession.isOngoing) {
            return
        }
        lastManualFollowInterruptionAtMs = SystemClock.elapsedRealtime()
    }

    fun stopTtsForNavigation() {
        if (currentBookTtsSession.isOngoing) {
            ttsController.stopByNavigation()
        }
    }

    val isCurrentBookTtsPlaying = currentBookTtsSession.isSpeaking
    val isCurrentBookTtsPaused = currentBookTtsSession.isPaused
    val isCurrentBookTtsOngoing = currentBookTtsSession.isOngoing
    fun toggleListening() {
        bumpChromeInteraction()
        when {
            ttsRuntime.isVoicePreviewing -> ttsController.stopByUser()
            isCurrentBookTtsPaused -> ttsController.requestResumePlayback()
            isCurrentBookTtsPlaying -> ttsController.requestPausePlayback()
            ttsRuntime.currentBookId == state.book.id && ttsRuntime.playbackState == ReaderTtsSessionState.FAILED -> retryListening()
            else -> startTtsFromCurrentLocation()
        }
    }
    val livePlaybackVisualRange = ReaderTtsActiveVisualRangeResolver.resolveLiveRange(
        runtimeState = ttsRuntime,
        currentBookSession = currentBookTtsSession,
    )
    val activePlaybackVisualRange = ReaderTtsActiveVisualRangeResolver.resolveActiveRange(
        pendingRange = pendingRestartVisualRange,
        liveRange = livePlaybackVisualRange,
    )
    val isFollowSuppressed = ReaderTtsFollowSuppressionPolicy.shouldSuppressFollow(
        lastInterruptionAtMs = lastManualFollowInterruptionAtMs,
        nowMs = SystemClock.elapsedRealtime(),
    )
    val readerTtsUiState = ReaderTtsReaderUiStateResolver.resolve(
        currentBookId = state.book.id,
        runtimeState = ttsRuntime,
    )
    val ttsToggleUiState = readerTtsUiState.toggleState.copy(
        speechRate = selectedSettings.ttsSettings.speechRate,
    )
    val ttsStatusText = readerTtsUiState.statusText
    val remainingTimeLabel = readerTtsUiState.remainingTimeLabel
    val listeningError = localListeningError ?: ttsRuntime.localErrorMessage
        ?.takeIf { ttsRuntime.currentBookId == state.book.id }

    LaunchedEffect(chromeMode, lastChromeInteractionAtMs) {
        val interactionAt = lastChromeInteractionAtMs
        val remainingDelay = ReaderChromeAutoHidePolicy.remainingDelayMillis(
            chromeMode = chromeMode,
            lastInteractionAtMs = interactionAt,
            nowMs = SystemClock.elapsedRealtime(),
        ) ?: return@LaunchedEffect
        if (remainingDelay > 0L) {
            delay(remainingDelay)
        }
        if (
            ReaderChromeAutoHidePolicy.shouldHideAfterDelay(
                chromeMode = chromeMode,
                scheduledInteractionAtMs = interactionAt ?: return@LaunchedEffect,
                currentInteractionAtMs = lastChromeInteractionAtMs,
            )
        ) {
            setChromeMode(ReaderChromeMode.READING_ONLY, refreshAutoHide = false)
        }
    }

    LaunchedEffect(pendingRestartVisualRange) {
        val pendingRange = pendingRestartVisualRange ?: return@LaunchedEffect
        delay(8_000)
        if (pendingRestartVisualRange == pendingRange) {
            pendingRestartVisualRange = null
        }
    }

    LaunchedEffect(
        pendingRestartVisualRange,
        livePlaybackVisualRange,
        ttsRuntime.currentBookId,
        ttsRuntime.playbackState,
    ) {
        val pendingRange = pendingRestartVisualRange ?: return@LaunchedEffect
        if (
            ReaderTtsRestartVisualRangeClearancePolicy.shouldClearPendingRange(
                pendingRange = pendingRange,
                liveRange = livePlaybackVisualRange,
                currentBookSession = currentBookTtsSession,
            )
        ) {
            pendingRestartVisualRange = null
        }
    }

    DisposableEffect(state.book.id) {
        onDispose {
            val latestRuntime = ttsController.runtimeState.value
            if (latestRuntime.currentBookId == state.book.id) {
                if (latestRuntime.playbackState != ReaderTtsSessionState.FAILED && !latestRuntime.localErrorMessage.isNullOrBlank()) {
                    ttsController.clearLocalErrorMessage()
                }
                if (!latestRuntime.localStatusMessage.isNullOrBlank()) {
                    ttsController.clearLocalStatusMessage()
                }
            }
        }
    }

    LaunchedEffect(lastManualFollowInterruptionAtMs, isCurrentBookTtsOngoing) {
        val interruptionAt = lastManualFollowInterruptionAtMs ?: return@LaunchedEffect
        if (!isCurrentBookTtsPlaying) {
            return@LaunchedEffect
        }
        val remainingDelay = ReaderTtsFollowSuppressionPolicy.remainingSuppressionMillis(
            lastInterruptionAtMs = interruptionAt,
            nowMs = SystemClock.elapsedRealtime(),
        ) ?: return@LaunchedEffect
        if (remainingDelay > 0L) {
            delay(remainingDelay)
        }
        if (
            ReaderTtsFollowSuppressionPolicy.shouldClearExpiredSuppression(
                scheduledInterruptionAtMs = interruptionAt,
                currentInterruptionAtMs = lastManualFollowInterruptionAtMs,
                nowMs = SystemClock.elapsedRealtime(),
            )
        ) {
            lastManualFollowInterruptionAtMs = null
        }
    }

    val pageInsets = WindowInsets.safeDrawing.asPaddingValues()
    val pageViewport = ReaderPageViewportMetricsResolver.resolve(viewportSize.height,
        with(rootDensity) { ReaderPageTopPadding.roundToPx() },
        with(rootDensity) { ReaderPageBottomPadding.roundToPx() },
        with(rootDensity) { pageInsets.calculateTopPadding().roundToPx() },
        with(rootDensity) { pageInsets.calculateBottomPadding().roundToPx() })
    val requestedPageLayout = ReaderPageLayoutKey(
        with(rootDensity) { (viewportSize.width.toDp() - ReaderHorizontalPadding * 2).roundToPx() },
        pageViewport.availableHeightPx, rootDensity.density, rootDensity.fontScale,
        readerSettings.fontSizeSp,
        (readerSettings.fontSizeSp * readerSettings.lineHeightMultiplier) / readerSettings.fontSizeSp,
        with(rootDensity) { (readerSettings.fontSizeSp * readerSettings.paragraphSpacingEm).coerceIn(8f, 36f).dp.toPx() })

    // Prepare PAGE without mounting a second body while the confirmed SCROLL remains visible.
    LaunchedEffect(position.request?.id, readerSettings.readingMode, requestedPageLayout, displayMode) {
        val request = position.request ?: return@LaunchedEffect
        if (readerSettings.readingMode != ReadingMode.PAGE || displayMode != ReadingMode.SCROLL ||
            requestedPageLayout.widthPx <= 0 || requestedPageLayout.heightPx <= 0) return@LaunchedEffect
        try {
            pageState.prepare(request, requestedPageLayout, state.chapters.map { it.chapterIndex })
            if (position.request?.id == request.id && readerSettings.readingMode == ReadingMode.PAGE) {
                preparedPageRequestId = request.id
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            position.fail(request.id, failure.message ?: "分页准备失败，可以重试。")
        }
    }

    LaunchedEffect(state.book.id, position.request?.id, scrollFeed, canLocateScroll, scrollLayoutKey) {
        val request = position.request ?: return@LaunchedEffect
        if (!canLocateScroll) return@LaunchedEffect
        try {
            scrollToAnchor(request.anchor)
            if (position.confirm(request.id, visibleScrollAnchor() ?: request.anchor)) {
                rememberScrollViewport(position.confirmedAnchor)
                if (pendingChromeAutoHideRefreshAfterRestore && chromeMode == ReaderChromeMode.CHROME_VISIBLE) {
                    lastChromeInteractionAtMs = SystemClock.elapsedRealtime()
                    pendingChromeAutoHideRefreshAfterRestore = false
                }
                queueCurrentProgress()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            position.fail(request.id, failure.message ?: "定位失败，可以重试。")
        }
    }

    val recoveryKey = if (position.error != null && displayMode == ReadingMode.SCROLL) {
        ReaderScrollRecoveryKey(position.error!!, contentRetry, position.confirmedAnchor, scrollLayoutKey, viewportSize)
    } else null
    var completedRecovery by remember(state.book.id) { mutableStateOf<ReaderScrollRecoveryKey?>(null) }
    LaunchedEffect(recoveryKey) {
        val recovery = recoveryKey ?: return@LaunchedEffect
        val previous = confirmedScrollViewport ?: return@LaunchedEffect
        try {
            withTimeout(10_000) {
                if (previous.bookId == state.book.id && previous.content == scrollFeed &&
                    previous.layout == scrollLayoutKey && previous.viewport == viewportSize) {
                    scrollListState.scrollToItem(previous.itemIndex, previous.itemOffset)
                } else {
                    // Pixel offsets belong to one layout only; the confirmed word survives a new layout.
                    scrollToAnchor(previous.anchor)
                }
            }
            completedRecovery = recovery
        } catch (cancelled: CancellationException) {
            // A timeout ends this one recovery attempt; a new request still cancels its work normally.
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
        } catch (_: Exception) {
            // Retain the original error and retry target. Never turn a failed recovery into navigation.
        }
    }

    LaunchedEffect(state.book.id, scrollFeed, restoredPosition, canLocateScroll, isCurrentBookTtsPlaying, scrollLayoutKey) {
        if (!canLocateScroll || !restoredPosition) return@LaunchedEffect
        snapshotFlow { scrollListState.isScrollInProgress to visibleScrollAnchor() }
            .distinctUntilChanged()
            .collect { (isScrolling, anchor) ->
                if (anchor != null && position.recordViewport(anchor)) rememberScrollViewport(anchor)
                if (isScrolling && isCurrentBookTtsPlaying && !isProgrammaticScrollFollowInFlight) markManualFollowInterruption()
                if (!isScrolling) {
                    isProgrammaticScrollFollowInFlight = false
                    queueCurrentProgress()
                }
            }
    }

    LaunchedEffect(activePlaybackVisualRange, isCurrentBookTtsPlaying, isFollowSuppressed, readerSettings.readingMode, readerForeground) {
        val range = activePlaybackVisualRange ?: return@LaunchedEffect
        if (!readerForeground || !isCurrentBookTtsPlaying || isFollowSuppressed || position.error != null || !canLocateScroll) return@LaunchedEffect
        val current = visibleScrollAnchor()
        if (current?.chapterIndex == range.chapterIndex && kotlin.math.abs(current.charOffset - range.startCharOffset) < 48) return@LaunchedEffect
        isProgrammaticScrollFollowInFlight = true
        position.jump(ReadingAnchor(range.chapterIndex, range.startCharOffset))
    }

    LaunchedEffect(
        activePlaybackVisualRange,
        isCurrentBookTtsPlaying,
        isFollowSuppressed,
        readerSettings.readingMode,
        selectedChapterIndex,
        readerForeground,
    ) {
        val activeRange = activePlaybackVisualRange ?: return@LaunchedEffect
        if (!readerForeground || !isCurrentBookTtsPlaying || isFollowSuppressed || position.error != null || readerSettings.readingMode != ReadingMode.PAGE) {
            return@LaunchedEffect
        }
        if (activeRange.chapterIndex != selectedChapterIndex) {
            openChapter(
                chapterIndex = activeRange.chapterIndex,
                nextChromeMode = chromeMode,
                restoreCharOffset = activeRange.startCharOffset,
            )
        }
    }

    val latestQueueCurrentProgress by rememberUpdatedState { queueCurrentProgress() }
    DisposableEffect(
        lifecycleOwner,
        state.book.id,
        selectedChapterIndex,
        scrollFeed,
        restoredPosition,
        readerSettings.readingMode,
        position.confirmedAnchor,
    ) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    readerForeground = true
                    val interruptionAt = lastManualFollowInterruptionAtMs
                    if (
                        interruptionAt != null &&
                        ReaderTtsFollowSuppressionPolicy.shouldClearExpiredSuppression(
                            scheduledInterruptionAtMs = interruptionAt,
                            currentInterruptionAtMs = lastManualFollowInterruptionAtMs,
                            nowMs = SystemClock.elapsedRealtime(),
                        )
                    ) {
                        lastManualFollowInterruptionAtMs = null
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    readerForeground = false
                    latestQueueCurrentProgress()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .onSizeChanged { size ->
                if (viewportSize != IntSize.Zero && size != viewportSize && position.error == null) position.reflow()
                viewportSize = size
            }
            .background(themePalette.background),
    ) {
        when (displayMode) {
            ReadingMode.SCROLL -> ScrollReaderContent(
                chapters = scrollFeed,
                modifier = if (retainPageUntilScrollConfirmed) {
                    Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { }
                } else Modifier,
                userScrollEnabled = canLocateScroll && position.request == null,
                layoutKey = scrollLayoutKey,
                author = state.book.author,
                themePalette = themePalette,
                fontSize = contentFontSize,
                lineHeight = contentLineHeight,
                paragraphSpacing = paragraphSpacing,
                listState = scrollListState,
                activeHighlightRangeByChapter = activePlaybackVisualRange
                    ?.let { activeRange ->
                        mapOf(
                            activeRange.chapterIndex to ReaderTtsCharacterRange(
                                startCharOffset = activeRange.startCharOffset,
                                endCharOffset = activeRange.endCharOffset,
                            ),
                        )
                    }
                    .orEmpty(),
                enableLongPressRestart = isCurrentBookTtsOngoing && !retainPageUntilScrollConfirmed,
                onRestartFromLocation = { chapterIndex, charOffset ->
                    restartTtsFromPressedOffset(
                        chapterIndex = chapterIndex,
                        pressedCharOffset = charOffset,
                    )
                },
                onBodyMetricsChanged = onScrollBodyMetricsChanged,
                onBodyMetricsDisposed = onScrollBodyMetricsDisposed,
                onCenterTap = {
                    if (!retainPageUntilScrollConfirmed) {
                        setChromeMode(ReaderChromeStateReducer.onCenterTap(chromeMode))
                    }
                },
            )

            ReadingMode.PAGE -> StablePageReaderContent(
                bookId = state.book.id,
                chapters = state.chapters,
                repository = repository,
                position = position,
                pageState = pageState,
                executeRequests = readerSettings.readingMode == ReadingMode.PAGE && position.error == null,
                onAnchorResolved = if (selectedSettings.readingMode != ReadingMode.PAGE && position.error == null) {
                    { id, anchor ->
                        if (position.resolvedAnchor(id, anchor)) readerSettings = selectedSettings
                    }
                } else null,
                themePalette = themePalette,
                fontSize = contentFontSize,
                lineHeight = contentLineHeight,
                paragraphSpacing = paragraphSpacing,
                activeHighlight = activePlaybackVisualRange,
                followTargetCharOffset = resolvePageFollowTargetCharOffset(
                    playbackSnapshot = ttsRuntime.playbackSnapshot,
                    selectedChapterIndex = selectedChapterIndex,
                    isFollowSuppressed = isFollowSuppressed || !readerForeground || !isCurrentBookTtsPlaying,
                ),
                enableLongPress = isCurrentBookTtsOngoing,
                dismissExpandedChrome = chromeMode == ReaderChromeMode.SETTINGS_EXPANDED ||
                    chromeMode == ReaderChromeMode.LISTENING_EXPANDED,
                onRestartFromLocation = { chapterIndex, charOffset -> restartTtsFromPressedOffset(chapterIndex, charOffset) },
                onManualFollowInterruption = ::markManualFollowInterruption,
                onCrossChapter = ::stopTtsForNavigation,
                onConfirmed = { _, _, _ ->
                    confirmedMode = ReadingMode.PAGE
                    confirmedDisplaySettings = readerSettings
                    queueCurrentProgress()
                    if (pendingChromeAutoHideRefreshAfterRestore && chromeMode == ReaderChromeMode.CHROME_VISIBLE) {
                        lastChromeInteractionAtMs = SystemClock.elapsedRealtime()
                        pendingChromeAutoHideRefreshAfterRestore = false
                    }
                },
                onToggleChrome = { setChromeMode(ReaderChromeStateReducer.onCenterTap(chromeMode)) },
            )
        }

        if (retainPageUntilScrollConfirmed) {
            val previous = pageState.display.confirmedWindow
            val page = previous?.pages?.firstOrNull { it.id == pageState.display.confirmedPageId }
            val look = previous?.appearance
            val fitsViewport = look != null && look.layout.widthPx == requestedPageLayout.widthPx &&
                look.layout.heightPx == requestedPageLayout.heightPx &&
                look.layout.density == requestedPageLayout.density && look.layout.fontScale == requestedPageLayout.fontScale &&
                with(rootDensity) { look.topPadding.roundToPx() } == pageViewport.topPaddingPx &&
                with(rootDensity) { look.bottomPadding.roundToPx() } == pageViewport.bottomPaddingPx
            Box(
                modifier = Modifier.matchParentSize().background(themePalette.background).readerBodyTapInput { offset, size ->
                    if (chromeMode == ReaderChromeMode.SETTINGS_EXPANDED ||
                        ReaderTapZone.resolve(offset.x, size.width.toFloat()) == ReaderTapZone.TOGGLE_CHROME) {
                        setChromeMode(ReaderChromeStateReducer.onCenterTap(chromeMode))
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (page != null && look != null && fitsViewport) {
                    ReaderPageSurface(
                        page = page.slice,
                        themePalette = themePalette,
                        fontSize = look.fontSize,
                        lineHeight = look.lineHeight,
                        paragraphSpacing = look.paragraphSpacing,
                        pageTopPadding = look.topPadding,
                        pageBottomPadding = look.bottomPadding,
                        highlightRange = activePlaybackVisualRange?.takeIf { it.chapterIndex == page.chapter.chapterIndex }
                            ?.let { ReaderTtsCharacterRange(it.startCharOffset, it.endCharOffset) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else Text("正在定位正文…", color = themePalette.content)
            }
        }

        if (displayMode == ReadingMode.SCROLL &&
            ((scrollFeed.isEmpty() && position.error == null) || (recoveryKey != null && completedRecovery != recoveryKey))) {
            Box(Modifier.matchParentSize().background(themePalette.background), contentAlignment = Alignment.Center) {
                if (position.error == null) Text("正在准备正文…", color = themePalette.content)
            }
        }

        if (brightnessOverlayAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = brightnessOverlayAlpha)),
            )
        }

        position.error?.let { message ->
            Surface(modifier = Modifier.align(Alignment.Center).padding(24.dp), color = themePalette.background) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message, color = themePalette.content)
                    Button(onClick = { contentRetry += 1; position.retry() }) { Text("重试") }
                    Button(onClick = ::leaveReader) { Text("返回书架") }
                }
            }
        }

        if (chromeMode == ReaderChromeMode.READING_ONLY) {
            ReaderImmersiveHeader(
                chapterTitle = selectedChapter?.title ?: "正文",
                themePalette = themePalette,
                onBack = ::leaveReader,
            )
        } else {
            ReaderTopBar(
                bookTitle = state.book.title,
                chapterTitle = selectedChapter?.title ?: "正文",
                themePalette = themePalette,
                onBack = ::leaveReader,
            )
        }

        if (chromeMode != ReaderChromeMode.READING_ONLY || ttsToggleUiState.showImmersiveAction) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                ReaderControls(
                    chromeMode = chromeMode,
                    appearanceMode = selectedSettings.appearanceMode,
                    progressSummary = progressSummary,
                    showChapterNavigationRow = displayMode == ReadingMode.PAGE,
                    canOpenPreviousChapter = adjacentChapter(-1) != null,
                    canOpenNextChapter = adjacentChapter(1) != null,
                    themePalette = themePalette,
                    ttsToggleState = ttsToggleUiState,
                    onOpenPreviousChapter = {
                        bumpChromeInteraction()
                        stopTtsForNavigation()
                        openAdjacentChapter(
                            delta = -1,
                            nextChromeMode = ReaderChromeStateReducer.onChapterStep(chromeMode),
                            refreshChromeAutoHide = true,
                        )
                    },
                    onOpenNextChapter = {
                        bumpChromeInteraction()
                        stopTtsForNavigation()
                        openAdjacentChapter(
                            delta = 1,
                            nextChromeMode = ReaderChromeStateReducer.onChapterStep(chromeMode),
                            refreshChromeAutoHide = true,
                        )
                    },
                    onOpenToc = {
                        val nextChromeMode = ReaderChromeStateReducer.onOpenDirectory(chromeMode)
                        setChromeMode(
                            nextChromeMode,
                            refreshAutoHide = nextChromeMode == ReaderChromeMode.CHROME_VISIBLE,
                        )
                    },
                    onToggleAppearanceMode = {
                        bumpChromeInteraction()
                        toggleAppearanceMode()
                    },
                    onOpenSettings = {
                        val nextChromeMode = ReaderChromeStateReducer.onOpenSettings(chromeMode)
                        setChromeMode(
                            nextChromeMode,
                            refreshAutoHide = nextChromeMode == ReaderChromeMode.CHROME_VISIBLE,
                        )
                    },
                    onToggleTts = {
                        if (!isCurrentBookTtsOngoing && !(ttsRuntime.currentBookId == state.book.id &&
                            ttsRuntime.playbackState == ReaderTtsSessionState.FAILED)) startTtsFromCurrentLocation()
                        setChromeMode(ReaderChromeMode.LISTENING_EXPANDED)
                    },
                    onImmersiveTtsAction = ::toggleListening,
                    onOpenListening = { setChromeMode(ReaderChromeMode.LISTENING_EXPANDED) },
                    onOpenListeningSettings = { setChromeMode(ReaderChromeMode.LISTENING_EXPANDED) },
                    onStopTts = if (isCurrentBookTtsOngoing || ttsRuntime.isVoicePreviewing) {
                        { bumpChromeInteraction(); ttsController.stopByUser() }
                    } else null,
                    onCloseExpanded = { setChromeMode(ReaderChromeMode.CHROME_VISIBLE, refreshAutoHide = true) },
                    expandedContent = when (chromeMode) {
                        ReaderChromeMode.SETTINGS_EXPANDED -> {
                            {
                                ReaderSettingsSheet(
                                    settings = requestedSettings,
                                    themePalette = themePalette,
                                    onUpdateSettings = ::updateReaderSettings,
                                )
                            }
                        }

                        ReaderChromeMode.LISTENING_EXPANDED -> {
                            {
                                ReaderTtsSettingsSheet(
                                    statusText = if (localListeningError != null) "暂时无法开始" else ttsStatusText,
                                    settings = selectedSettings.ttsSettings,
                                    themePalette = themePalette,
                                    remainingTimeLabel = remainingTimeLabel,
                                    primaryActionLabel = when {
                                        isCurrentBookTtsOngoing -> ttsToggleUiState.immersiveActionLabel
                                        listeningError != null -> "重试朗读"
                                        else -> "开始朗读"
                                    },
                                    onPrimaryAction = ::toggleListening,
                                    onStop = if (isCurrentBookTtsOngoing) ttsController::stopByUser else null,
                                    onClose = { setChromeMode(ReaderChromeMode.READING_ONLY, refreshAutoHide = false) },
                                    onUpdateSpeechRate = { updateTtsSettings(selectedSettings.ttsSettings.copy(speechRate = it)) },
                                    onUpdatePitch = { updateTtsSettings(selectedSettings.ttsSettings.copy(pitch = it)) },
                                    onUpdateTimerPreset = { updateTtsSettings(selectedSettings.ttsSettings.copy(timerPreset = it)) },
                                    errorText = listeningError,
                                )
                            }
                        }

                        ReaderChromeMode.DIRECTORY_OPEN -> {
                            {
                                ReaderTocSheet(
                                    bookTitle = state.book.title,
                                    chapters = state.chapters,
                                    selectedChapterIndex = selectedChapterIndex,
                                    themePalette = themePalette,
                                    onSelectChapter = { chapterIndex ->
                                        stopTtsForNavigation()
                                        openChapter(
                                            chapterIndex = chapterIndex,
                                            nextChromeMode = ReaderChromeStateReducer.onDirectoryChapterSelected(),
                                        )
                                    },
                                )
                            }
                        }

                        else -> null
                    },
                )
            }
        }
    }
}

@Composable
private fun ScrollReaderContent(
    chapters: List<ReaderFeedChapterContent>,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean,
    layoutKey: ReaderScrollLayoutKey,
    author: String?,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    listState: LazyListState,
    activeHighlightRangeByChapter: Map<Int, ReaderTtsCharacterRange>,
    enableLongPressRestart: Boolean,
    onRestartFromLocation: (chapterIndex: Int, charOffset: Int) -> Unit,
    onBodyMetricsChanged: (chapterIndex: Int, bodyMetrics: ReaderScrollBodyMetrics) -> Unit,
    onBodyMetricsDisposed: (chapterIndex: Int) -> Unit,
    onCenterTap: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(top = ReaderScrollViewportTopInset)
            .readerBodyTapInput { offset, size ->
                if (ReaderTapZone.resolve(offset.x, size.width.toFloat()) == ReaderTapZone.TOGGLE_CHROME) {
                    onCenterTap()
                }
            },
        state = listState,
        userScrollEnabled = userScrollEnabled,
    ) {
        itemsIndexed(
            items = chapters,
            key = { _, item -> item.chapter.chapterIndex },
        ) { index, chapterContent ->
            // Invalidate measurements together with their real Text nodes. Clearing only the
            // remembered callbacks can wait forever when unchanged Text reuses its layout.
            key(layoutKey, chapterContent.text) {
                val chapterTopPadding = if (index == 0) ReaderTopPadding else 24.dp
                val chapterTopPaddingPx = with(LocalDensity.current) { chapterTopPadding.roundToPx() }
                var bodyMetrics by remember {
                    mutableStateOf(ReaderScrollBodyMetrics(0, 0))
                }
                var renderedLines by remember {
                    mutableStateOf(emptyList<ReaderPageLine>())
                }
                val readyMetrics = bodyMetrics.copy(lines = renderedLines)
                SideEffect {
                    if (readyMetrics.heightPx > 0 && readyMetrics.lines.isNotEmpty()) {
                        onBodyMetricsChanged(chapterContent.chapter.chapterIndex, readyMetrics)
                    }
                }
                DisposableEffect(chapterContent.chapter.chapterIndex) {
                    onDispose {
                        onBodyMetricsDisposed(chapterContent.chapter.chapterIndex)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = ReaderHorizontalPadding,
                            end = ReaderHorizontalPadding,
                            top = chapterTopPadding,
                            bottom = if (index == chapters.lastIndex) ReaderBottomPadding else 12.dp,
                        ),
                ) {
                    if (index == 0 && !author.isNullOrBlank()) {
                        Text(
                            text = "作者：$author",
                            color = themePalette.content.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                    Text(
                        text = chapterContent.chapter.title,
                        color = themePalette.content.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            bodyMetrics = ReaderScrollBodyMetrics(
                                // positionInParent is inside the padded Column; LazyColumn measures the outer item.
                                topWithinItemPx = chapterTopPaddingPx + coordinates.positionInParent().y.roundToInt(),
                                heightPx = coordinates.size.height,
                            )
                        },
                    ) {
                        ReaderParagraphContent(
                            text = chapterContent.text,
                            themePalette = themePalette,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            paragraphSpacing = paragraphSpacing,
                            highlightRange = activeHighlightRangeByChapter[chapterContent.chapter.chapterIndex],
                            onLinesChanged = { renderedLines = it },
                            onLongPressCharOffset = if (enableLongPressRestart) {
                                { charOffset ->
                                    onRestartFromLocation(chapterContent.chapter.chapterIndex, charOffset)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReaderPageSurface(
    page: ReaderPageSlice,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    pageTopPadding: androidx.compose.ui.unit.Dp,
    pageBottomPadding: androidx.compose.ui.unit.Dp,
    highlightRange: ReaderTtsCharacterRange?,
    modifier: Modifier = Modifier,
    onTapText: (() -> Unit)? = null,
    onLongPressCharOffset: ((Int) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .padding(
                start = ReaderHorizontalPadding,
                end = ReaderHorizontalPadding,
                top = pageTopPadding,
                bottom = pageBottomPadding,
            ),
    ) {
        ReaderPageParagraphContent(
            page = page,
            themePalette = themePalette,
            fontSize = fontSize,
            lineHeight = lineHeight,
            paragraphSpacing = paragraphSpacing,
            highlightRange = highlightRange,
            onTapText = onTapText,
            onLongPressCharOffset = onLongPressCharOffset,
        )
    }
}

@Composable
private fun ReaderPageParagraphContent(
    page: ReaderPageSlice,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    highlightRange: ReaderTtsCharacterRange?,
    onTapText: (() -> Unit)? = null,
    onLongPressCharOffset: ((Int) -> Unit)? = null,
) {
    ReaderParagraphContent(
        text = page.rawText,
        baseCharOffset = page.startCharOffset,
        firstParagraphContinues = page.firstParagraphContinues,
        themePalette = themePalette,
        fontSize = fontSize,
        lineHeight = lineHeight,
        paragraphSpacing = paragraphSpacing,
        highlightRange = highlightRange,
        includeFontPadding = false,
        onTapText = onTapText,
        onLongPressCharOffset = onLongPressCharOffset,
    )
}

@Composable
private fun ReaderParagraphContent(
    text: String,
    baseCharOffset: Int = 0,
    firstParagraphContinues: Boolean = false,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    highlightRange: ReaderTtsCharacterRange? = null,
    includeFontPadding: Boolean = true,
    onTapText: (() -> Unit)? = null,
    onLongPressCharOffset: ((Int) -> Unit)? = null,
    onLinesChanged: ((List<ReaderPageLine>) -> Unit)? = null,
) {
    val paragraphs = remember(text) { ReaderParagraphModel.toDisplayParagraphs(text) }
    val paragraphTextStyle = remember(fontSize, lineHeight, includeFontPadding) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            textAlign = TextAlign.Start,
            platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding),
        )
    }
    val measuredLines = remember(text, firstParagraphContinues, fontSize, lineHeight, paragraphSpacing, LocalDensity.current) {
        mutableStateMapOf<Int, List<ReaderPageLine>>()
    }
    if (onLinesChanged != null && paragraphs.all { measuredLines.containsKey(it.startCharOffset) }) {
        val allLines = paragraphs.flatMap { measuredLines.getValue(it.startCharOffset) }
        SideEffect { onLinesChanged(allLines) }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
    ) {
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val indentedStyle = paragraphTextStyle.withReaderParagraphIndent(
                indentFirstLine = paragraphIndex > 0 || !firstParagraphContinues,
            )
            var textLayoutResult by remember(paragraph, indentedStyle, paragraphSpacing, LocalDensity.current) {
                mutableStateOf<TextLayoutResult?>(null)
            }
            var topPx by remember(paragraph, fontSize, lineHeight, paragraphSpacing, LocalDensity.current) { mutableStateOf<Float?>(null) }
            if (onLinesChanged != null) {
                val layout = textLayoutResult
                val top = topPx
                SideEffect {
                    if (layout != null && top != null) {
                        measuredLines[paragraph.startCharOffset] = List(layout.lineCount) { line ->
                            ReaderPageLine(
                                (baseCharOffset + paragraph.startCharOffset + layout.getLineStart(line)).coerceAtMost(baseCharOffset + paragraph.endCharOffset),
                                (baseCharOffset + paragraph.startCharOffset + layout.getLineEnd(line, visibleEnd = true)).coerceAtMost(baseCharOffset + paragraph.endCharOffset),
                                top + layout.getLineTop(line), top + layout.getLineBottom(line),
                            )
                        }
                    }
                }
            }
            Text(
                text = ReaderHighlightAnnotator.annotate(
                    text = paragraph.text,
                    textStartCharOffset = baseCharOffset + paragraph.startCharOffset,
                    textEndCharOffset = baseCharOffset + paragraph.endCharOffset,
                    highlightRange = highlightRange,
                    highlightColor = themePalette.content.copy(alpha = 0.18f),
                ),
                modifier = (if (
                    (onLongPressCharOffset != null || onTapText != null) &&
                    paragraph.endCharOffset > paragraph.startCharOffset
                ) {
                    Modifier.pointerInput(paragraph, onTapText, onLongPressCharOffset) {
                        detectTapGestures(
                            onTap = {
                                onTapText?.invoke()
                            },
                            onLongPress = { pressOffset ->
                                val restartFromOffset = onLongPressCharOffset ?: return@detectTapGestures
                                val layoutResult = textLayoutResult ?: return@detectTapGestures
                                val localCharOffset = layoutResult.resolveBodyCharOffset(pressOffset)
                                restartFromOffset(
                                    baseCharOffset + paragraph.startCharOffset + localCharOffset,
                                )
                            },
                        )
                    }
                } else {
                    Modifier
                }).then(if (onLinesChanged != null) Modifier.onGloballyPositioned { topPx = it.positionInParent().y } else Modifier),
                color = themePalette.content,
                style = indentedStyle,
                onTextLayout = { textLayoutResult = it },
            )
        }
    }
}

private fun TextLayoutResult.resolveBodyCharOffset(
    pressOffset: Offset,
): Int {
    val lineIndex = getLineForVerticalPosition(
        pressOffset.y.coerceIn(0f, size.height.toFloat().coerceAtLeast(1f) - 1f),
    )
    val lineStartOffset = getLineStart(lineIndex)
    val lineEndOffsetExclusive = getLineEnd(lineIndex, visibleEnd = true)
        .coerceAtLeast(lineStartOffset + 1)
    val lineLeft = getBoundingBox(lineStartOffset).left
    val lineRight = getBoundingBox(lineEndOffsetExclusive - 1).right
    val clampedX = if (lineRight > lineLeft) {
        pressOffset.x.coerceIn(lineLeft, lineRight - 0.5f)
    } else {
        lineLeft
    }
    val lineTop = getLineTop(lineIndex)
    val lineBottom = getLineBottom(lineIndex)
    val clampedY = if (lineBottom > lineTop) {
        pressOffset.y.coerceIn(lineTop, lineBottom - 0.5f)
    } else {
        lineTop
    }
    val maxCharOffset = layoutInput.text.length
        .coerceAtLeast(1) - 1
    return getOffsetForPosition(Offset(clampedX, clampedY))
        .coerceIn(0, maxCharOffset)
}

private fun buildScrollVisibleChapterItems(
    listState: LazyListState,
    scrollFeed: List<ReaderFeedChapterContent>,
    bodyMetricsByChapter: Map<Int, ReaderScrollBodyMetrics>,
): List<ReaderVisibleChapterItem> {
    val layoutInfo = listState.layoutInfo
    val rawItems = layoutInfo.visibleItemsInfo.map { item ->
        ReaderRawVisibleScrollItem(
            itemIndex = item.index,
            offsetPx = item.offset - layoutInfo.viewportStartOffset,
            sizePx = item.size,
        )
    }
    val chapterIndexByItemIndex = layoutInfo.visibleItemsInfo.mapNotNull { item ->
        val chapterIndex = scrollFeed.getOrNull(item.index)?.chapter?.chapterIndex
            ?: return@mapNotNull null
        item.index to chapterIndex
    }.toMap()
    return ReaderScrollVisibleItemBuilder.build(
        rawItems = rawItems,
        chapterIndexByItemIndex = chapterIndexByItemIndex,
        bodyMetricsByChapter = bodyMetricsByChapter,
    )
}

private fun ReaderVisibleChapterItem.bodyOffsetWithinItemPx(): Int {
    return (bodyOffsetPx - offsetPx).coerceIn(0, sizePx.coerceAtLeast(0))
}

private data class ReaderFeedKey(val bookId: String, val mode: ReadingMode, val chapterIndex: Int?, val retry: Int)

private sealed interface ReaderFeedResult {
    val key: ReaderFeedKey
    data class Loading(override val key: ReaderFeedKey) : ReaderFeedResult
    data class Ready(override val key: ReaderFeedKey, val content: List<ReaderFeedChapterContent>) : ReaderFeedResult
    data class Failed(override val key: ReaderFeedKey, val message: String) : ReaderFeedResult
}

private data class ReaderScrollSnapshot(
    val bookId: String,
    val content: List<ReaderFeedChapterContent>,
    val anchor: ReadingAnchor,
    val layout: ReaderScrollLayoutKey,
    val viewport: IntSize,
    val itemIndex: Int,
    val itemOffset: Int,
)

private data class ReaderScrollRecoveryKey(
    val error: String,
    val retry: Int,
    val anchor: ReadingAnchor,
    val layout: ReaderScrollLayoutKey,
    val viewport: IntSize,
)

private data class ReaderFeedChapterContent(
    val chapter: Chapter,
    val text: String,
)

/** Scroll line geometry is independent of viewport height and overlay controls. */
private data class ReaderScrollLayoutKey(
    val widthPx: Int,
    val density: Float,
    val fontScale: Float,
    val fontSizeSp: Int,
    val lineHeightMultiplier: Float,
    val paragraphSpacingEm: Float,
)

private sealed interface ReaderScreenState {
    data object Loading : ReaderScreenState

    data object NotFound : ReaderScreenState

    data class Ready(
        val book: Book,
        val chapters: List<Chapter>,
        val initialChapterIndex: Int,
        val initialCharOffset: Int,
    ) : ReaderScreenState
}

internal fun paginatePageSlices(
    content: String,
    availableWidthPx: Int,
    availableHeightPx: Int,
    paragraphSpacingPx: Float,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    cancellationContext: CoroutineContext = EmptyCoroutineContext,
): List<ReaderPageSlice> = paginateMeasuredPageSlices(
    content, availableWidthPx, availableHeightPx, paragraphSpacingPx,
    textMeasurer, textStyle, cancellationContext,
)

@Composable
private fun ReaderSystemBarsEffect(
    themePalette: ReaderThemePalette,
) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    val window = activity.window
    val insetsController = WindowCompat.getInsetsController(window, view)
    val nextStyle = ReaderSystemBarStyleResolver.resolve(themePalette)

    DisposableEffect(window, insetsController) {
        val originalStatusBarColor = window.statusBarColor
        val originalNavigationBarColor = window.navigationBarColor
        val originalLightStatusBars = insetsController.isAppearanceLightStatusBars
        val originalLightNavigationBars = insetsController.isAppearanceLightNavigationBars
        val originalNavContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }

        onDispose {
            window.statusBarColor = originalStatusBarColor
            window.navigationBarColor = originalNavigationBarColor
            insetsController.isAppearanceLightStatusBars = originalLightStatusBars
            insetsController.isAppearanceLightNavigationBars = originalLightNavigationBars
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && originalNavContrastEnforced != null) {
                window.isNavigationBarContrastEnforced = originalNavContrastEnforced
            }
        }
    }

    SideEffect {
        window.statusBarColor = nextStyle.barColorArgb
        window.navigationBarColor = nextStyle.barColorArgb
        insetsController.isAppearanceLightStatusBars = nextStyle.useDarkIcons
        insetsController.isAppearanceLightNavigationBars = nextStyle.useDarkIcons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = nextStyle.enforceNavigationBarContrast
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
