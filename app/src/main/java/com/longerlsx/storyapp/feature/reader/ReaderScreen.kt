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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
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
import com.longerlsx.storyapp.feature.reader.tts.restartVisualRangeOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
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
    var activeSettingsTab by rememberSaveable(state.book.id) {
        mutableStateOf<ReaderSettingsTab?>(null)
    }
    var lastChromeInteractionAtMs by remember(state.book.id) {
        mutableStateOf<Long?>(null)
    }
    var readerSettings by remember {
        mutableStateOf(settingsStore.load())
    }
    var pendingChromeAutoHideRefreshAfterRestore by remember(state.book.id) {
        mutableStateOf(false)
    }
    val restoredPosition = position.hasConfirmedLayout && position.request == null && position.error == null
    var deferredReadingMode by remember(state.book.id) { mutableStateOf<ReadingMode?>(null) }
    val requestedSettings = readerSettings.copy(readingMode = deferredReadingMode ?: readerSettings.readingMode)
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
    var localRestartFeedbackMessage by remember(state.book.id) {
        mutableStateOf<String?>(null)
    }
    var isProgrammaticScrollFollowInFlight by remember(state.book.id) {
        mutableStateOf(false)
    }
    val ttsRuntime by ttsController.runtimeState.collectAsState()

    LaunchedEffect(chromeMode, activeSettingsTab, ttsRuntime.availableVoices) {
        if (
            chromeMode == ReaderChromeMode.SETTINGS_EXPANDED &&
            activeSettingsTab == ReaderSettingsTab.TTS &&
            ttsRuntime.availableVoices.isEmpty()
        ) {
            storyApplication.preloadReaderTtsVoices()
        }
    }

    val selectedChapterPosition = state.chapters.indexOfFirst { it.chapterIndex == selectedChapterIndex }
    val selectedChapter = state.chapters.firstOrNull { it.chapterIndex == selectedChapterIndex }
    val scrollFeedState = produceState<List<ReaderFeedChapterContent>>(
        initialValue = emptyList(),
        key1 = state.book.id,
        key2 = readerSettings.readingMode,
        key3 = (if (readerSettings.readingMode == ReadingMode.PAGE) selectedChapterIndex else -1) to contentRetry,
    ) {
        try {
            val needed = if (readerSettings.readingMode == ReadingMode.SCROLL) state.chapters
                else state.chapters.filter { it.chapterIndex == selectedChapterIndex }
            value = needed.map { chapter ->
                ReaderFeedChapterContent(chapter, repository.getChapterText(state.book.id, chapter.chapterIndex)
                    ?: error("无法读取第 ${chapter.chapterIndex + 1} 章正文。"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (readerSettings.readingMode == ReadingMode.SCROLL) {
                position.request?.let { position.fail(it.id, failure.message ?: "正文读取失败") }
            }
        }
    }
    val scrollFeed = scrollFeedState.value
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
        fontSizeSp = readerSettings.fontSizeSp,
        lineHeightMultiplier = readerSettings.lineHeightMultiplier,
        paragraphSpacingEm = readerSettings.paragraphSpacingEm,
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
    var confirmedScrollViewport by remember(state.book.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    val themePalette = ReaderThemeResolver.resolveActivePalette(readerSettings)
    val contentFontSize = readerSettings.fontSizeSp.sp
    val contentLineHeight = (readerSettings.fontSizeSp * readerSettings.lineHeightMultiplier).sp
    val paragraphSpacing = (readerSettings.fontSizeSp * readerSettings.paragraphSpacingEm).coerceIn(8f, 36f).dp
    // Controls overlay a fixed reading viewport; showing chrome is not navigation.
    val scrollReadableViewportTopPx = with(rootDensity) { ReaderScrollReadableTopSpacing.roundToPx() }
    val progressSummary = ReaderChapterProgressFormatter.format(
        chapters = state.chapters,
        selectedChapterPosition = selectedChapterPosition,
    )
    val activeBrightness = ReaderBrightnessResolver.resolveActiveBrightness(readerSettings)
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

    fun captureProgress(): ReadingProgress? {
        if (!position.hasConfirmedLayout) return null
        if (readerSettings.readingMode == ReadingMode.SCROLL && position.request == null && position.error == null) {
            visibleScrollAnchor()?.let(position::recordViewport)
        }
        return ReadingProgress(state.book.id, position.confirmedAnchor, readerSettings.readingMode, System.currentTimeMillis())
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

    fun openAdjacentChapter(
        delta: Int,
        nextChromeMode: ReaderChromeMode,
        refreshChromeAutoHide: Boolean = false,
    ) {
        val baseChapter = position.request?.anchor?.chapterIndex ?: selectedChapterIndex
        val chapter = state.chapters.getOrNull(state.chapters.indexOfFirst { it.chapterIndex == baseChapter } + delta) ?: return
        openChapter(
            chapterIndex = chapter.chapterIndex,
            nextChromeMode = nextChromeMode,
            refreshChromeAutoHide = refreshChromeAutoHide,
        )
    }

    fun updateReaderSettings(requested: ReaderSettings) {
        val postponeMode = requested.readingMode != readerSettings.readingMode && position.request?.turns?.isNotEmpty() == true
        deferredReadingMode = requested.readingMode.takeIf { postponeMode }
        val next = if (postponeMode) requested.copy(readingMode = readerSettings.readingMode) else requested
        if (next == readerSettings) return
        val needsLayout = next.readingMode != readerSettings.readingMode ||
            next.fontSizeSp != readerSettings.fontSizeSp ||
            next.lineHeightMultiplier != readerSettings.lineHeightMultiplier ||
            next.paragraphSpacingEm != readerSettings.paragraphSpacingEm
        if (needsLayout) {
            if (readerSettings.readingMode == ReadingMode.SCROLL && position.request == null) {
                visibleScrollAnchor()?.let(position::recordViewport)
            }
            position.reflow()
        }
        readerSettings = next
        settingsStore.save(next)
    }

    LaunchedEffect(position.request) {
        if (position.request == null) {
            deferredReadingMode?.let { mode ->
                deferredReadingMode = null
                updateReaderSettings(readerSettings.copy(readingMode = mode))
            }
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
        if (chromeMode == ReaderChromeMode.DIRECTORY_OPEN || chromeMode == ReaderChromeMode.SETTINGS_EXPANDED) {
            chromeMode = ReaderChromeMode.CHROME_VISIBLE
        } else leaveReader()
    }

    fun updateTtsSettings(nextTtsSettings: ReaderTtsSettings) {
        if (nextTtsSettings == readerSettings.ttsSettings) {
            return
        }
        val nextSettings = readerSettings.copy(ttsSettings = nextTtsSettings)
        readerSettings = nextSettings
        settingsStore.save(nextSettings)
        ttsController.applySettings(nextTtsSettings)
    }

    fun toggleAppearanceMode() {
        updateReaderSettings(
            requestedSettings.copy(
                appearanceMode = if (readerSettings.appearanceMode == ReaderAppearanceMode.DAY) {
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

    fun buildTtsStartRequest(
        explicitLocation: ReaderTextStartLocation? = null,
    ): ReaderTtsStartRequest {
        val anchor = if (readerSettings.readingMode == ReadingMode.SCROLL && position.request == null) {
            visibleScrollAnchor() ?: position.confirmedAnchor
        } else position.confirmedAnchor
        val startLocation = explicitLocation ?: ReaderTextStartLocation(anchor.chapterIndex, anchor.charOffset)
        return ReaderTtsStartRequestFactory.create(
            book = state.book,
            chapters = state.chapters,
            startLocation = startLocation,
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingNotificationPermissionStartRequest ?: return@rememberLauncherForActivityResult
        pendingNotificationPermissionStartRequest = null
        ttsController.start(
            request = request,
            settings = readerSettings.ttsSettings,
            notificationControlsAvailable = granted,
        )
    }

    fun startTtsFromCurrentLocation() {
        val request = buildTtsStartRequest()
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
            settings = readerSettings.ttsSettings,
            notificationControlsAvailable = true,
        )
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
        localRestartFeedbackMessage = "从这里重新朗读"
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
            ttsController.stopByNavigation("已停止朗读，重新开始将从当前位置开始")
        }
    }

    val isCurrentBookTtsPlaying = currentBookTtsSession.isSpeaking
    val isCurrentBookTtsPaused = currentBookTtsSession.isPaused
    val isCurrentBookTtsOngoing = currentBookTtsSession.isOngoing
    val ttsVoiceSelection = ReaderTtsVoiceSelectionResolver.resolve(
        persistedVoiceName = readerSettings.ttsSettings.voiceName,
        availableVoices = ttsRuntime.availableVoices,
        availableVoicesLoaded = ttsRuntime.availableVoicesLoaded,
    )
    val selectedVoiceName = ttsVoiceSelection.selectedVoiceName
    val ttsSystemDefaultVoiceStatus = ttsVoiceSelection.systemDefaultVoiceStatus
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
    val ttsToggleUiState = readerTtsUiState.toggleState
    val ttsStatusText = readerTtsUiState.statusText
    val remainingTimeLabel = readerTtsUiState.remainingTimeLabel
    val transientTtsMessage = ReaderTtsTransientMessageResolver.resolve(
        currentBookId = state.book.id,
        runtimeState = ttsRuntime,
        localRestartFeedbackMessage = localRestartFeedbackMessage,
    )

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

    LaunchedEffect(transientTtsMessage) {
        if (transientTtsMessage == null) {
            return@LaunchedEffect
        }
        delay(2_500)
        val latestRuntime = ttsController.runtimeState.value
        val clearance = ReaderTtsTransientMessageClearancePolicy.resolve(
            currentBookId = state.book.id,
            runtimeState = latestRuntime,
            displayedMessage = transientTtsMessage,
        )
        if (clearance.clearLocalErrorMessage) {
            ttsController.clearLocalErrorMessage()
        }
        if (clearance.clearLocalStatusMessage) {
            ttsController.clearLocalStatusMessage()
        }
    }

    LaunchedEffect(localRestartFeedbackMessage) {
        val latestMessage = localRestartFeedbackMessage ?: return@LaunchedEffect
        delay(1_500)
        if (localRestartFeedbackMessage == latestMessage) {
            localRestartFeedbackMessage = null
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
                if (!latestRuntime.localErrorMessage.isNullOrBlank()) {
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

    LaunchedEffect(state.book.id, position.request?.id, scrollFeed, readerSettings.readingMode, scrollLayoutKey) {
        val request = position.request ?: return@LaunchedEffect
        if (readerSettings.readingMode != ReadingMode.SCROLL || scrollFeed.isEmpty()) return@LaunchedEffect
        val targetIndex = scrollFeed.indexOfFirst { it.chapter.chapterIndex == request.anchor.chapterIndex }
        if (targetIndex < 0) {
            position.fail(request.id, "目标章节不存在")
            return@LaunchedEffect
        }
        try {
            snapshotFlow { scrollListState.layoutInfo.totalItemsCount }.first { it > targetIndex }
            if (scrollListState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
                scrollListState.scrollToItem(targetIndex)
            }
            val item = snapshotFlow {
                buildScrollVisibleChapterItems(scrollListState, scrollFeed, scrollBodyMetricsByChapter)
                    .firstOrNull { it.itemIndex == targetIndex }
                    ?.takeIf { scrollBodyMetricsByChapter[it.chapterIndex]?.lines?.isNotEmpty() == true }
            }.filterNotNull().first()
            val lines = scrollBodyMetricsByChapter.getValue(request.anchor.chapterIndex).lines
            val offset = (item.bodyOffsetWithinItemPx() +
                ReaderLineAnchorMapper.topForCharOffset(lines, request.anchor.charOffset).roundToInt() -
                scrollReadableViewportTopPx).coerceAtLeast(0)
            scrollListState.scrollToItem(targetIndex, offset)
            androidx.compose.runtime.withFrameNanos { }
            if (position.confirm(request.id, visibleScrollAnchor() ?: request.anchor)) {
                confirmedScrollViewport = scrollListState.firstVisibleItemIndex to scrollListState.firstVisibleItemScrollOffset
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

    LaunchedEffect(position.error) {
        if (position.error != null && readerSettings.readingMode == ReadingMode.SCROLL) {
            confirmedScrollViewport?.let { (index, offset) -> scrollListState.scrollToItem(index, offset) }
        }
    }

    LaunchedEffect(state.book.id, scrollFeed, restoredPosition, readerSettings.readingMode, isCurrentBookTtsPlaying, scrollLayoutKey) {
        if (readerSettings.readingMode != ReadingMode.SCROLL || !restoredPosition) return@LaunchedEffect
        snapshotFlow { scrollListState.isScrollInProgress to visibleScrollAnchor() }
            .distinctUntilChanged()
            .collect { (isScrolling, anchor) ->
                if (anchor != null && position.recordViewport(anchor)) {
                    confirmedScrollViewport = scrollListState.firstVisibleItemIndex to scrollListState.firstVisibleItemScrollOffset
                }
                if (isScrolling && isCurrentBookTtsPlaying && !isProgrammaticScrollFollowInFlight) markManualFollowInterruption()
                if (!isScrolling) {
                    isProgrammaticScrollFollowInFlight = false
                    queueCurrentProgress()
                }
            }
    }

    LaunchedEffect(activePlaybackVisualRange, isCurrentBookTtsPlaying, isFollowSuppressed, readerSettings.readingMode) {
        val range = activePlaybackVisualRange ?: return@LaunchedEffect
        if (!isCurrentBookTtsPlaying || isFollowSuppressed || readerSettings.readingMode != ReadingMode.SCROLL) return@LaunchedEffect
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
    ) {
        val activeRange = activePlaybackVisualRange ?: return@LaunchedEffect
        if (!isCurrentBookTtsPlaying || isFollowSuppressed || readerSettings.readingMode != ReadingMode.PAGE) {
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
                if (viewportSize != IntSize.Zero && size != viewportSize) position.reflow()
                viewportSize = size
            }
            .background(themePalette.background),
    ) {
        when (readerSettings.readingMode) {
            ReadingMode.SCROLL -> ScrollReaderContent(
                chapters = scrollFeed,
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
                enableLongPressRestart = isCurrentBookTtsOngoing,
                onRestartFromLocation = { chapterIndex, charOffset ->
                    restartTtsFromPressedOffset(
                        chapterIndex = chapterIndex,
                        pressedCharOffset = charOffset,
                    )
                },
                onBodyMetricsChanged = onScrollBodyMetricsChanged,
                onBodyMetricsDisposed = onScrollBodyMetricsDisposed,
                onCenterTap = {
                    setChromeMode(ReaderChromeStateReducer.onCenterTap(chromeMode))
                },
            )

            ReadingMode.PAGE -> StablePageReaderContent(
                bookId = state.book.id,
                chapters = state.chapters,
                repository = repository,
                position = position,
                themePalette = themePalette,
                fontSize = contentFontSize,
                lineHeight = contentLineHeight,
                paragraphSpacing = paragraphSpacing,
                activeHighlight = activePlaybackVisualRange,
                followTargetCharOffset = resolvePageFollowTargetCharOffset(
                    playbackSnapshot = ttsRuntime.playbackSnapshot,
                    selectedChapterIndex = selectedChapterIndex,
                    isFollowSuppressed = isFollowSuppressed,
                ),
                enableLongPress = isCurrentBookTtsOngoing,
                dismissExpandedChrome = chromeMode == ReaderChromeMode.SETTINGS_EXPANDED,
                onRestartFromLocation = { chapterIndex, charOffset -> restartTtsFromPressedOffset(chapterIndex, charOffset) },
                onManualFollowInterruption = ::markManualFollowInterruption,
                onCrossChapter = ::stopTtsForNavigation,
                onConfirmed = { _, _, _ ->
                    queueCurrentProgress()
                    if (pendingChromeAutoHideRefreshAfterRestore && chromeMode == ReaderChromeMode.CHROME_VISIBLE) {
                        lastChromeInteractionAtMs = SystemClock.elapsedRealtime()
                        pendingChromeAutoHideRefreshAfterRestore = false
                    }
                },
                onToggleChrome = { setChromeMode(ReaderChromeStateReducer.onCenterTap(chromeMode)) },
            )
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

        if (!transientTtsMessage.isNullOrBlank()) {
            ReaderTransientTtsMessage(
                message = transientTtsMessage,
                themePalette = themePalette,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
            )
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
                    appearanceMode = readerSettings.appearanceMode,
                    progressSummary = progressSummary,
                    showChapterNavigationRow = readerSettings.readingMode == ReadingMode.PAGE,
                    canOpenPreviousChapter = selectedChapterPosition > 0,
                    canOpenNextChapter = selectedChapterPosition >= 0 && selectedChapterPosition < state.chapters.lastIndex,
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
                        activeSettingsTab = ReaderSettingsTabResolver.resolveOnOpen(
                            currentTab = activeSettingsTab,
                            isCurrentBookTtsOngoing = isCurrentBookTtsOngoing,
                        )
                        val nextChromeMode = ReaderChromeStateReducer.onOpenSettings(chromeMode)
                        setChromeMode(
                            nextChromeMode,
                            refreshAutoHide = nextChromeMode == ReaderChromeMode.CHROME_VISIBLE,
                        )
                    },
                    onToggleTts = {
                        bumpChromeInteraction()
                        if (isCurrentBookTtsPaused) {
                            ttsController.requestResumePlayback()
                        } else if (isCurrentBookTtsPlaying) {
                            ttsController.stopByUser()
                        } else {
                            startTtsFromCurrentLocation()
                        }
                    },
                    onImmersiveTtsAction = {
                        if (isCurrentBookTtsPaused) {
                            ttsController.requestResumePlayback()
                        } else if (isCurrentBookTtsPlaying) {
                            ttsController.stopByUser()
                        }
                    },
                    expandedContent = when (chromeMode) {
                        ReaderChromeMode.SETTINGS_EXPANDED -> {
                            {
                                ReaderSettingsSheet(
                                    settings = requestedSettings,
                                    themePalette = themePalette,
                                    activeTab = activeSettingsTab ?: ReaderSettingsTab.READING,
                                    availableVoices = ttsRuntime.availableVoices,
                                    availableVoicesLoaded = ttsRuntime.availableVoicesLoaded,
                                    selectedVoiceName = selectedVoiceName,
                                    ttsStatusText = ttsStatusText,
                                    ttsRemainingTimeLabel = remainingTimeLabel,
                                    ttsSystemDefaultVoiceStatus = ttsSystemDefaultVoiceStatus,
                                    onSelectTab = { activeSettingsTab = it },
                                    onUpdateSettings = ::updateReaderSettings,
                                    onUpdateTtsSettings = ::updateTtsSettings,
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
private fun ReaderTransientTtsMessage(
    message: String,
    themePalette: ReaderThemePalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = themePalette.surface.copy(alpha = 0.96f),
    ) {
        ReaderSingleLineText(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = themePalette.content,
        )
    }
}

@Composable
private fun ScrollReaderContent(
    chapters: List<ReaderFeedChapterContent>,
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
        modifier = Modifier
            .fillMaxSize()
            .padding(top = ReaderScrollViewportTopInset)
            .readerBodyTapInput { offset, size ->
                if (ReaderTapZone.resolve(offset.x, size.width.toFloat()) == ReaderTapZone.TOGGLE_CHROME) {
                    onCenterTap()
                }
            },
        state = listState,
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
    val measuredLines = remember(text, fontSize, lineHeight, paragraphSpacing, LocalDensity.current) {
        mutableStateMapOf<Int, List<ReaderPageLine>>()
    }
    if (onLinesChanged != null && paragraphs.all { measuredLines.containsKey(it.startCharOffset) }) {
        val allLines = paragraphs.flatMap { measuredLines.getValue(it.startCharOffset) }
        SideEffect { onLinesChanged(allLines) }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
    ) {
        paragraphs.forEach { paragraph ->
            var textLayoutResult by remember(paragraph, fontSize, lineHeight, paragraphSpacing, LocalDensity.current) {
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
                style = paragraphTextStyle,
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
): List<ReaderPageSlice> {
    cancellationContext.ensureActive()
    if (content.isBlank() || availableWidthPx <= 0) {
        return listOf(
            ReaderPageSlice(
                startCharOffset = 0,
                endCharOffset = 0,
                text = "当前章节暂无正文。",
            ),
        )
    }

    val lines = ReaderPageTextLayout.measureLines(
        content = content,
        availableWidthPx = availableWidthPx,
        textMeasurer = textMeasurer,
        textStyle = textStyle,
        paragraphSpacingPx = paragraphSpacingPx,
        cancellationContext = cancellationContext,
    )
    return ReaderPageLinePaginator.paginate(
        content = content,
        lines = lines,
        availableHeightPx = availableHeightPx.coerceAtLeast(1).toFloat(),
        pageFitsViewport = { page ->
            pageTextFitsViewport(
                page = page,
                availableWidthPx = availableWidthPx,
                availableHeightPx = availableHeightPx,
                paragraphSpacingPx = paragraphSpacingPx,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                cancellationContext = cancellationContext,
            )
        },
        cancellationContext = cancellationContext,
    )
}

private fun pageTextFitsViewport(
    page: ReaderPageSlice,
    availableWidthPx: Int,
    availableHeightPx: Int,
    paragraphSpacingPx: Float,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    cancellationContext: CoroutineContext,
): Boolean {
    val lines = ReaderPageTextLayout.measureLines(
        content = page.rawText,
        availableWidthPx = availableWidthPx,
        textMeasurer = textMeasurer,
        textStyle = textStyle,
        paragraphSpacingPx = paragraphSpacingPx,
        cancellationContext = cancellationContext,
    )
    if (lines.isEmpty()) {
        return true
    }
    return lines.last().bottomPx <= availableHeightPx
}

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
