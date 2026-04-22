package com.longerlsx.storyapp.feature.reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsTimeLabelFormatter
import com.longerlsx.storyapp.feature.reader.tts.activeVisualRangeOrNull
import com.longerlsx.storyapp.feature.reader.tts.isOngoingSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

private val ReaderHorizontalPadding = 24.dp
private val ReaderScrollViewportTopInset = 52.dp
private val ReaderTopPadding = 12.dp
private val ReaderBottomPadding = 96.dp
private val ReaderPageTopPadding = 44.dp
private val ReaderPageBottomPadding = 56.dp

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
    val storyApplication = remember(context) {
        context.applicationContext as StoryApplication
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selectedChapterIndex by remember(state.book.id) {
        mutableIntStateOf(state.initialChapterIndex)
    }
    var pendingRestoreCharOffset by remember(state.book.id) {
        mutableIntStateOf(state.initialCharOffset)
    }
    var currentPageIndex by remember(state.book.id) {
        mutableIntStateOf(0)
    }
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
    var restoreToLastPageOnOpen by remember(state.book.id) {
        mutableStateOf(false)
    }
    var pendingChromeAutoHideRefreshAfterRestore by remember(state.book.id) {
        mutableStateOf(false)
    }
    var restoredPosition by remember(state.book.id, readerSettings.readingMode) {
        mutableStateOf(false)
    }
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
    val currentContentState = produceState<ReaderLoadedChapterContent?>(
        initialValue = null,
        key1 = state.book.id,
        key2 = selectedChapterIndex,
    ) {
        value = null
        value = if (selectedChapter == null) {
            ReaderLoadedChapterContent(
                chapterIndex = selectedChapterIndex,
                text = "",
            )
        } else {
            ReaderLoadedChapterContent(
                chapterIndex = selectedChapterIndex,
                text = repository.getChapterText(state.book.id, selectedChapterIndex).orEmpty(),
            )
        }
    }
    val currentContent = currentContentState.value?.text.orEmpty()
    val scrollFeedState = produceState<List<ReaderFeedChapterContent>>(
        initialValue = emptyList(),
        key1 = state.book.id,
    ) {
        value = state.chapters.map { chapter ->
            ReaderFeedChapterContent(
                chapter = chapter,
                text = repository.getChapterText(state.book.id, chapter.chapterIndex).orEmpty(),
            )
        }
    }
    val scrollFeed = scrollFeedState.value
    val chapterTextByIndex = remember(scrollFeed) {
        scrollFeed.associate { it.chapter.chapterIndex to it.text }
    }
    val previousChapter = state.chapters.getOrNull(selectedChapterPosition - 1)
    val nextChapter = state.chapters.getOrNull(selectedChapterPosition + 1)
    val previousChapterText = scrollFeed.getOrNull(selectedChapterPosition - 1)?.text.orEmpty()
    val nextChapterText = scrollFeed.getOrNull(selectedChapterPosition + 1)?.text.orEmpty()
    val initialScrollItemIndex = remember(state.book.id, state.initialChapterIndex, state.chapters) {
        state.chapters.indexOfFirst { it.chapterIndex == state.initialChapterIndex }
            .takeIf { it >= 0 }
            ?: 0
    }
    var currentPages by remember(
        state.book.id,
        selectedChapterIndex,
        readerSettings.readingMode,
    ) {
        mutableStateOf(emptyList<ReaderPageSlice>())
    }
    val scrollListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollItemIndex,
    )
    val themePalette = ReaderThemeResolver.resolveActivePalette(readerSettings)
    val contentFontSize = readerSettings.fontSizeSp.sp
    val contentLineHeight = (readerSettings.fontSizeSp * readerSettings.lineHeightMultiplier).sp
    val paragraphSpacing = (readerSettings.fontSizeSp * readerSettings.paragraphSpacingEm).coerceIn(8f, 36f).dp
    val progressSummary = if (selectedChapterPosition >= 0) {
        "${selectedChapterPosition + 1}/${state.chapters.size}章"
    } else {
        "--/--"
    }
    val activeBrightness = ReaderBrightnessResolver.resolveActiveBrightness(readerSettings)
    val brightnessOverlayAlpha = ((1f - activeBrightness).coerceIn(0f, 1f) * 0.42f)

    ReaderSystemBarsEffect(themePalette = themePalette)

    suspend fun saveAnchor(
        chapterIndex: Int,
        charOffset: Int,
        readingMode: ReadingMode,
    ) {
        repository.saveReadingProgress(
            ReadingProgress(
                bookId = state.book.id,
                anchor = ReadingAnchor(
                    chapterIndex = chapterIndex,
                    charOffset = charOffset,
                ),
                readingMode = readingMode,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun saveScrollProgress() {
        val visibleItems = scrollListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            scrollFeed.getOrNull(item.index)?.let { chapterContent ->
                ReaderVisibleChapterItem(
                    itemIndex = item.index,
                    chapterIndex = chapterContent.chapter.chapterIndex,
                    offsetPx = item.offset - scrollListState.layoutInfo.viewportStartOffset,
                    sizePx = item.size,
                )
            }
        }
        val activeChapterIndex = ReaderActiveChapterResolver.resolve(
            visibleItems = visibleItems,
            chapters = state.chapters,
            viewportTopPx = 0,
        ) ?: return
        val activeItem = visibleItems.firstOrNull { it.chapterIndex == activeChapterIndex } ?: return
        val activeContent = scrollFeed.firstOrNull { it.chapter.chapterIndex == activeChapterIndex } ?: return
        val charOffset = ReaderScrollFeedAnchorMapper.toCharOffset(
            contentLength = activeContent.text.length,
            itemOffsetPx = activeItem.offsetPx,
            itemHeightPx = activeItem.sizePx,
        )
        saveAnchor(
            chapterIndex = activeChapterIndex,
            charOffset = charOffset,
            readingMode = ReadingMode.SCROLL,
        )
    }

    suspend fun savePageProgress(pageIndex: Int) {
        val charOffset = ReaderPageAnchorMapper.anchorForPageIndex(
            pages = currentPages,
            pageIndex = pageIndex,
        )
        saveAnchor(
            chapterIndex = selectedChapterIndex,
            charOffset = charOffset,
            readingMode = ReadingMode.PAGE,
        )
    }

    suspend fun saveCurrentProgress() {
        when (readerSettings.readingMode) {
            ReadingMode.SCROLL -> saveScrollProgress()
            ReadingMode.PAGE -> savePageProgress(currentPageIndex)
        }
    }

    fun openChapter(
        chapterIndex: Int,
        nextChromeMode: ReaderChromeMode,
        restoreCharOffset: Int = 0,
        restoreToLastPage: Boolean = false,
        refreshChromeAutoHide: Boolean = false,
    ) {
        selectedChapterIndex = chapterIndex
        pendingRestoreCharOffset = restoreCharOffset
        currentPageIndex = 0
        restoredPosition = false
        restoreToLastPageOnOpen = restoreToLastPage
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
        val chapter = state.chapters.getOrNull(selectedChapterPosition + delta) ?: return
        openChapter(
            chapterIndex = chapter.chapterIndex,
            nextChromeMode = nextChromeMode,
            refreshChromeAutoHide = refreshChromeAutoHide,
        )
    }

    fun updateReaderSettings(next: ReaderSettings) {
        val currentScrollVisibleItems = scrollListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            scrollFeed.getOrNull(item.index)?.let { chapterContent ->
                ReaderVisibleChapterItem(
                    itemIndex = item.index,
                    chapterIndex = chapterContent.chapter.chapterIndex,
                    offsetPx = item.offset - scrollListState.layoutInfo.viewportStartOffset,
                    sizePx = item.size,
                )
            }
        }
        val currentScrollChapterIndex = ReaderActiveChapterResolver.resolve(
            visibleItems = currentScrollVisibleItems,
            chapters = state.chapters,
            viewportTopPx = 0,
        ) ?: selectedChapterIndex
        val currentAnchorOffset = when (readerSettings.readingMode) {
            ReadingMode.SCROLL -> {
                val currentScrollItem = currentScrollVisibleItems.firstOrNull {
                    it.chapterIndex == currentScrollChapterIndex
                }
                val currentScrollContent = scrollFeed.firstOrNull {
                    it.chapter.chapterIndex == currentScrollChapterIndex
                }
                if (currentScrollItem != null && currentScrollContent != null) {
                    ReaderScrollFeedAnchorMapper.toCharOffset(
                        contentLength = currentScrollContent.text.length,
                        itemOffsetPx = currentScrollItem.offsetPx,
                        itemHeightPx = currentScrollItem.sizePx,
                    )
                } else {
                    pendingRestoreCharOffset
                }
            }

            ReadingMode.PAGE -> ReaderPageAnchorMapper.anchorForPageIndex(
                pages = currentPages,
                pageIndex = currentPageIndex,
            )
        }

        scope.launch {
            saveAnchor(
                chapterIndex = currentScrollChapterIndex,
                charOffset = currentAnchorOffset,
                readingMode = next.readingMode,
            )
        }
        selectedChapterIndex = currentScrollChapterIndex
        pendingRestoreCharOffset = currentAnchorOffset
        currentPageIndex = 0
        restoredPosition = false
        restoreToLastPageOnOpen = false
        readerSettings = next
        settingsStore.save(next)
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
            readerSettings.copy(
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
        val startLocation = explicitLocation ?: when (readerSettings.readingMode) {
            ReadingMode.SCROLL -> {
                val visibleItems = scrollListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    scrollFeed.getOrNull(item.index)?.let { chapterContent ->
                        ReaderVisibleChapterItem(
                            itemIndex = item.index,
                            chapterIndex = chapterContent.chapter.chapterIndex,
                            offsetPx = item.offset - scrollListState.layoutInfo.viewportStartOffset,
                            sizePx = item.size,
                        )
                    }
                }
                ReaderTextStartLocator.resolveScrollTopLocation(
                    visibleItems = visibleItems,
                    chapterTextByIndex = scrollFeed.associate { it.chapter.chapterIndex to it.text },
                    viewportTopPx = 0,
                ) ?: ReaderTextStartLocation(
                    chapterIndex = selectedChapterIndex,
                    charOffset = pendingRestoreCharOffset,
                )
            }

            ReadingMode.PAGE -> {
                if (currentPages.isEmpty()) {
                    ReaderTextStartLocation(
                        chapterIndex = selectedChapterIndex,
                        charOffset = pendingRestoreCharOffset,
                    )
                } else {
                    ReaderTextStartLocator.resolvePageTopLocation(
                        chapterIndex = selectedChapterIndex,
                        pages = currentPages,
                        currentPageIndex = currentPageIndex,
                    )
                }
            }
        }
        val summary = state.chapters.firstOrNull { it.chapterIndex == startLocation.chapterIndex }?.title ?: "正文"
        return ReaderTtsStartRequest(
            bookId = state.book.id,
            bookTitle = state.book.title,
            chapterIndex = startLocation.chapterIndex,
            charOffset = startLocation.charOffset,
            chapterTitleOrSummary = summary,
            activeStateLabel = "朗读中",
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
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            pendingNotificationPermissionStartRequest = request
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        ttsController.start(
            request = request,
            settings = readerSettings.ttsSettings,
            notificationControlsAvailable = true,
        )
    }

    fun restartTtsFromLocation(location: ReaderTextStartLocation) {
        if (
            ttsRuntime.currentBookId != state.book.id ||
            !ttsRuntime.playbackState.isOngoingSession()
        ) {
            return
        }
        pendingRestartVisualRange = buildPendingRestartVisualRange(
            location = location,
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
        if (
            ttsRuntime.currentBookId != state.book.id ||
            !ttsRuntime.playbackState.isOngoingSession()
        ) {
            return
        }
        lastManualFollowInterruptionAtMs = SystemClock.elapsedRealtime()
    }

    fun stopTtsForNavigation() {
        if (
            ttsRuntime.currentBookId == state.book.id &&
            ttsRuntime.playbackState.isOngoingSession()
        ) {
            ttsController.stopByNavigation("已停止朗读，重新开始将从当前位置开始")
        }
    }

    val isCurrentBookTtsPlaying =
        ttsRuntime.currentBookId == state.book.id &&
            (
                ttsRuntime.playbackState == com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.STARTING ||
                    ttsRuntime.playbackState == com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PLAYING
                )
    val isCurrentBookTtsPaused =
        ttsRuntime.currentBookId == state.book.id &&
            (
                ttsRuntime.playbackState == com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PAUSED_BY_USER ||
                    ttsRuntime.playbackState == com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS
                )
    val isCurrentBookTtsOngoing = isCurrentBookTtsPlaying || isCurrentBookTtsPaused
    val selectedVoiceName = readerSettings.ttsSettings.voiceName
        ?.takeIf { voiceName ->
            ttsRuntime.availableVoices.any { it.name == voiceName }
        }
    val ttsSystemDefaultVoiceStatus = if (
        readerSettings.ttsSettings.voiceName != null && selectedVoiceName == null
    ) {
        "当前使用系统默认音色"
    } else {
        null
    }
    val livePlaybackVisualRange = if (isCurrentBookTtsPlaying) {
        ttsRuntime.playbackSnapshot.activeVisualRangeOrNull()
    } else {
        null
    }
    val activePlaybackVisualRange = pendingRestartVisualRange ?: livePlaybackVisualRange
    val isFollowSuppressed = ReaderTtsFollowSuppressionPolicy.shouldSuppressFollow(
        lastInterruptionAtMs = lastManualFollowInterruptionAtMs,
        nowMs = SystemClock.elapsedRealtime(),
    )
    val remainingTimeLabel = ttsRuntime.remainingTimerMillis?.let(ReaderTtsTimeLabelFormatter::formatRemainingMillis)
    val ttsToggleLabel = when {
        isCurrentBookTtsPaused && !remainingTimeLabel.isNullOrBlank() -> "继续朗读 · $remainingTimeLabel"
        isCurrentBookTtsPaused -> "继续朗读"
        isCurrentBookTtsPlaying && !remainingTimeLabel.isNullOrBlank() -> "停止朗读 · $remainingTimeLabel"
        isCurrentBookTtsPlaying -> "停止朗读"
        else -> "朗读"
    }
    val ttsStatusText = when {
        isCurrentBookTtsPaused -> "当前状态：已暂停"
        isCurrentBookTtsPlaying -> "当前状态：朗读中"
        else -> "当前状态：未朗读"
    }
    val ttsToggleUiState = ReaderTtsToggleUiState(
        actionLabel = ttsToggleLabel,
        showImmersiveAction = isCurrentBookTtsOngoing,
        immersiveActionLabel = ttsToggleLabel,
    )
    val transientTtsMessage = when {
        ttsRuntime.currentBookId == state.book.id && !ttsRuntime.localErrorMessage.isNullOrBlank() -> {
            ttsRuntime.localErrorMessage
        }

        !localRestartFeedbackMessage.isNullOrBlank() -> {
            localRestartFeedbackMessage
        }

        ttsRuntime.currentBookId == state.book.id && !ttsRuntime.localStatusMessage.isNullOrBlank() -> {
            ttsRuntime.localStatusMessage
        }

        else -> null
    }

    LaunchedEffect(chromeMode, lastChromeInteractionAtMs) {
        val interactionAt = lastChromeInteractionAtMs ?: return@LaunchedEffect
        if (chromeMode != ReaderChromeMode.CHROME_VISIBLE) {
            return@LaunchedEffect
        }
        val remainingDelay = (3_000L - (SystemClock.elapsedRealtime() - interactionAt))
            .coerceAtLeast(0L)
        if (remainingDelay > 0L) {
            delay(remainingDelay)
        }
        if (chromeMode == ReaderChromeMode.CHROME_VISIBLE && lastChromeInteractionAtMs == interactionAt) {
            setChromeMode(ReaderChromeMode.READING_ONLY, refreshAutoHide = false)
        }
    }

    LaunchedEffect(transientTtsMessage) {
        if (transientTtsMessage == null) {
            return@LaunchedEffect
        }
        delay(2_500)
        val latestRuntime = ttsController.runtimeState.value
        if (latestRuntime.localErrorMessage == transientTtsMessage) {
            ttsController.clearLocalErrorMessage()
        }
        if (latestRuntime.localStatusMessage == transientTtsMessage) {
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
        if (ttsRuntime.currentBookId != state.book.id || !ttsRuntime.playbackState.isOngoingSession()) {
            pendingRestartVisualRange = null
            return@LaunchedEffect
        }
        val liveRange = livePlaybackVisualRange ?: return@LaunchedEffect
        if (
            liveRange.chapterIndex == pendingRange.chapterIndex &&
            liveRange.startCharOffset <= pendingRange.startCharOffset &&
            liveRange.endCharOffset >= pendingRange.startCharOffset
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
        val remainingDelay = (10_000L - (SystemClock.elapsedRealtime() - interruptionAt))
            .coerceAtLeast(0L)
        if (remainingDelay > 0L) {
            delay(remainingDelay)
        }
        if (
            lastManualFollowInterruptionAtMs == interruptionAt &&
            !ReaderTtsFollowSuppressionPolicy.shouldSuppressFollow(
                lastInterruptionAtMs = interruptionAt,
                nowMs = SystemClock.elapsedRealtime(),
            )
        ) {
            lastManualFollowInterruptionAtMs = null
        }
    }

    LaunchedEffect(
        state.book.id,
        selectedChapterIndex,
        pendingRestoreCharOffset,
        scrollFeed,
        readerSettings.readingMode,
    ) {
        if (readerSettings.readingMode != ReadingMode.SCROLL || restoredPosition) {
            return@LaunchedEffect
        }
        if (scrollFeed.isEmpty()) {
            return@LaunchedEffect
        }
        val targetItemIndex = scrollFeed.indexOfFirst { it.chapter.chapterIndex == selectedChapterIndex }
            .takeIf { it >= 0 }
            ?: 0
        snapshotFlow { scrollListState.layoutInfo.totalItemsCount }
            .first { it > targetItemIndex }
        for (attempt in 0 until 4) {
            scrollListState.scrollToItem(targetItemIndex, 0)
            yield()
            if (scrollListState.firstVisibleItemIndex == targetItemIndex) {
                break
            }
        }
        val targetItemInfo = snapshotFlow {
            scrollListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItemIndex }
        }
            .filterNotNull()
            .first()
        val restoreOffset = ReaderScrollFeedAnchorMapper.toScrollOffsetPx(
            contentLength = scrollFeed[targetItemIndex].text.length,
            charOffset = pendingRestoreCharOffset,
            itemHeightPx = targetItemInfo.size,
        )
        for (attempt in 0 until 4) {
            scrollListState.scrollToItem(targetItemIndex, restoreOffset)
            yield()
            if (scrollListState.firstVisibleItemIndex == targetItemIndex) {
                break
            }
        }
        restoredPosition = true
        if (pendingChromeAutoHideRefreshAfterRestore && chromeMode == ReaderChromeMode.CHROME_VISIBLE) {
            lastChromeInteractionAtMs = SystemClock.elapsedRealtime()
            pendingChromeAutoHideRefreshAfterRestore = false
        }
        if (ReaderRestorePolicy.shouldBootstrapProgress(pendingRestoreCharOffset)) {
            saveScrollProgress()
        }
    }

    LaunchedEffect(
        state.book.id,
        scrollFeed,
        restoredPosition,
        readerSettings.readingMode,
        isCurrentBookTtsPlaying,
    ) {
        if (readerSettings.readingMode != ReadingMode.SCROLL || !restoredPosition) {
            return@LaunchedEffect
        }

        snapshotFlow {
            scrollListState.isScrollInProgress to scrollListState.layoutInfo.visibleItemsInfo.map { item ->
                ReaderVisibleChapterItem(
                    itemIndex = item.index,
                    chapterIndex = scrollFeed.getOrNull(item.index)?.chapter?.chapterIndex ?: -1,
                    offsetPx = item.offset - scrollListState.layoutInfo.viewportStartOffset,
                    sizePx = item.size,
                )
            }
        }
            .distinctUntilChanged()
            .collect { (isScrolling, visibleItems) ->
                val filteredVisibleItems = visibleItems.filter { it.chapterIndex >= 0 }
                val activeChapterIndex = ReaderActiveChapterResolver.resolve(
                    visibleItems = filteredVisibleItems,
                    chapters = state.chapters,
                    viewportTopPx = 0,
                )
                if (activeChapterIndex != null && activeChapterIndex != selectedChapterIndex) {
                    selectedChapterIndex = activeChapterIndex
                }
                if (isScrolling && isCurrentBookTtsPlaying && !isProgrammaticScrollFollowInFlight) {
                    markManualFollowInterruption()
                }
                if (!isScrolling && isProgrammaticScrollFollowInFlight) {
                    isProgrammaticScrollFollowInFlight = false
                }
                if (!isScrolling) {
                    saveScrollProgress()
                }
            }
    }

    LaunchedEffect(
        activePlaybackVisualRange,
        isCurrentBookTtsPlaying,
        isFollowSuppressed,
        readerSettings.readingMode,
        scrollFeed,
    ) {
        val activeRange = activePlaybackVisualRange ?: return@LaunchedEffect
        if (!isCurrentBookTtsPlaying || isFollowSuppressed || readerSettings.readingMode != ReadingMode.SCROLL) {
            return@LaunchedEffect
        }
        val targetItemIndex = scrollFeed.indexOfFirst { it.chapter.chapterIndex == activeRange.chapterIndex }
            .takeIf { it >= 0 }
            ?: return@LaunchedEffect
        val currentTopLocation = ReaderTextStartLocator.resolveScrollTopLocation(
            visibleItems = scrollListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                scrollFeed.getOrNull(item.index)?.let { chapterContent ->
                    ReaderVisibleChapterItem(
                        itemIndex = item.index,
                        chapterIndex = chapterContent.chapter.chapterIndex,
                        offsetPx = item.offset - scrollListState.layoutInfo.viewportStartOffset,
                        sizePx = item.size,
                    )
                }
            },
            chapterTextByIndex = scrollFeed.associate { it.chapter.chapterIndex to it.text },
            viewportTopPx = 0,
        )
        if (
            currentTopLocation?.chapterIndex == activeRange.chapterIndex &&
            kotlin.math.abs(currentTopLocation.charOffset - activeRange.startCharOffset) < 48
        ) {
            return@LaunchedEffect
        }

        isProgrammaticScrollFollowInFlight = true
        try {
            snapshotFlow { scrollListState.layoutInfo.totalItemsCount }
                .first { it > targetItemIndex }
            scrollListState.scrollToItem(targetItemIndex, 0)
            val targetItemInfo = snapshotFlow {
                scrollListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItemIndex }
            }
                .filterNotNull()
                .first()
            val targetOffset = ReaderScrollFeedAnchorMapper.toScrollOffsetPx(
                contentLength = scrollFeed[targetItemIndex].text.length,
                charOffset = activeRange.startCharOffset,
                itemHeightPx = targetItemInfo.size,
            )
            scrollListState.scrollToItem(targetItemIndex, targetOffset)
        } finally {
            isProgrammaticScrollFollowInFlight = false
        }
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

    DisposableEffect(
        lifecycleOwner,
        state.book.id,
        selectedChapterIndex,
        scrollFeed,
        restoredPosition,
        readerSettings.readingMode,
        currentPageIndex,
    ) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val interruptionAt = lastManualFollowInterruptionAtMs
                    if (
                        interruptionAt != null &&
                        !ReaderTtsFollowSuppressionPolicy.shouldSuppressFollow(
                            lastInterruptionAtMs = interruptionAt,
                            nowMs = SystemClock.elapsedRealtime(),
                        )
                    ) {
                        lastManualFollowInterruptionAtMs = null
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    if (restoredPosition) {
                        scope.launch {
                            saveCurrentProgress()
                        }
                    }
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
            .background(themePalette.background),
    ) {
        when (readerSettings.readingMode) {
            ReadingMode.SCROLL -> ScrollReaderContent(
                chapters = scrollFeed,
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
                onCenterTap = {
                    setChromeMode(ReaderChromeStateReducer.onCenterTap(chromeMode))
                },
            )

            ReadingMode.PAGE -> PageReaderContent(
                bookId = state.book.id,
                chapterIndex = selectedChapterIndex,
                contentLoaded = currentContentState.value?.chapterIndex == selectedChapterIndex,
                content = currentContent,
                previousChapterIndex = previousChapter?.chapterIndex,
                previousChapterContent = previousChapterText,
                nextChapterIndex = nextChapter?.chapterIndex,
                nextChapterContent = nextChapterText,
                restoreCharOffset = pendingRestoreCharOffset,
                restoreToLastPage = restoreToLastPageOnOpen,
                restoredPosition = restoredPosition,
                themePalette = themePalette,
                fontSize = contentFontSize,
                lineHeight = contentLineHeight,
                highlightRange = activePlaybackVisualRange
                    ?.takeIf { it.chapterIndex == selectedChapterIndex }
                    ?.let {
                        ReaderTtsCharacterRange(
                            startCharOffset = it.startCharOffset,
                            endCharOffset = it.endCharOffset,
                        )
                    },
                followTargetCharOffset = activePlaybackVisualRange
                    ?.takeIf { it.chapterIndex == selectedChapterIndex && !isFollowSuppressed }
                    ?.startCharOffset,
                enableTtsRestartGesture = isCurrentBookTtsOngoing,
                onRestartFromCharOffset = { charOffset ->
                    restartTtsFromPressedOffset(
                        chapterIndex = selectedChapterIndex,
                        pressedCharOffset = charOffset,
                    )
                },
                onManualFollowInterruption = ::markManualFollowInterruption,
                onPagesChanged = { currentPages = it },
                onRestored = { restoredPage ->
                    currentPageIndex = restoredPage
                    restoredPosition = true
                    restoreToLastPageOnOpen = false
                    if (pendingChromeAutoHideRefreshAfterRestore && chromeMode == ReaderChromeMode.CHROME_VISIBLE) {
                        lastChromeInteractionAtMs = SystemClock.elapsedRealtime()
                        pendingChromeAutoHideRefreshAfterRestore = false
                    }
                },
                onPageSettled = { pageIndex ->
                    currentPageIndex = pageIndex
                    if (restoredPosition) {
                        scope.launch {
                            savePageProgress(pageIndex)
                        }
                    }
                },
                onOpenPreviousBoundary = {
                    stopTtsForNavigation()
                    markManualFollowInterruption()
                    val boundaryPreviousChapter = state.chapters.getOrNull(selectedChapterPosition - 1) ?: return@PageReaderContent
                    openChapter(
                        chapterIndex = boundaryPreviousChapter.chapterIndex,
                        nextChromeMode = chromeMode,
                        restoreToLastPage = true,
                    )
                },
                onOpenNextBoundary = {
                    stopTtsForNavigation()
                    markManualFollowInterruption()
                    openAdjacentChapter(1, chromeMode)
                },
                onToggleChrome = {
                    setChromeMode(ReaderChromeStateReducer.onCenterTap(chromeMode))
                },
            )
        }

        if (brightnessOverlayAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = brightnessOverlayAlpha)),
            )
        }

        if (!transientTtsMessage.isNullOrBlank()) {
            ReaderTransientTtsMessage(
                message = transientTtsMessage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
            )
        }

        if (chromeMode == ReaderChromeMode.READING_ONLY) {
            ReaderImmersiveHeader(
                chapterTitle = selectedChapter?.title ?: "正文",
                themePalette = themePalette,
                onBack = {
                    scope.launch {
                        saveCurrentProgress()
                        onBack()
                    }
                },
            )
        } else {
            ReaderTopBar(
                bookTitle = state.book.title,
                chapterTitle = selectedChapter?.title ?: "正文",
                themePalette = themePalette,
                onBack = {
                    scope.launch {
                        saveCurrentProgress()
                        onBack()
                    }
                },
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
                        if (activeSettingsTab == null) {
                            activeSettingsTab = if (isCurrentBookTtsOngoing) {
                                ReaderSettingsTab.TTS
                            } else {
                                ReaderSettingsTab.READING
                            }
                        }
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
                                    settings = readerSettings,
                                    themePalette = themePalette,
                                    activeTab = activeSettingsTab ?: ReaderSettingsTab.READING,
                                    availableVoices = ttsRuntime.availableVoices,
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ScrollReaderContent(
    chapters: List<ReaderFeedChapterContent>,
    author: String?,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    listState: LazyListState,
    activeHighlightRangeByChapter: Map<Int, ReaderTtsCharacterRange>,
    enableLongPressRestart: Boolean,
    onRestartFromLocation: (chapterIndex: Int, charOffset: Int) -> Unit,
    onCenterTap: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = ReaderScrollViewportTopInset)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (ReaderTapZone.resolve(offset.x, size.width.toFloat()) == ReaderTapZone.TOGGLE_CHROME) {
                        onCenterTap()
                    }
                }
            },
        state = listState,
    ) {
        itemsIndexed(
            items = chapters,
            key = { _, item -> item.chapter.chapterIndex },
        ) { index, chapterContent ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = ReaderHorizontalPadding,
                        end = ReaderHorizontalPadding,
                        top = if (index == 0) ReaderTopPadding else 24.dp,
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
                ReaderParagraphContent(
                    text = chapterContent.text.ifBlank { "当前章节暂无正文。" },
                    themePalette = themePalette,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    paragraphSpacing = paragraphSpacing,
                    highlightRange = activeHighlightRangeByChapter[chapterContent.chapter.chapterIndex],
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

@Composable
private fun PageReaderContent(
    bookId: String,
    chapterIndex: Int,
    contentLoaded: Boolean,
    content: String,
    previousChapterIndex: Int?,
    previousChapterContent: String,
    nextChapterIndex: Int?,
    nextChapterContent: String,
    restoreCharOffset: Int,
    restoreToLastPage: Boolean,
    restoredPosition: Boolean,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    highlightRange: ReaderTtsCharacterRange?,
    followTargetCharOffset: Int?,
    enableTtsRestartGesture: Boolean,
    onRestartFromCharOffset: (Int) -> Unit,
    onManualFollowInterruption: () -> Unit,
    onPagesChanged: (List<ReaderPageSlice>) -> Unit,
    onRestored: (Int) -> Unit,
    onPageSettled: (Int) -> Unit,
    onOpenPreviousBoundary: () -> Unit,
    onOpenNextBoundary: () -> Unit,
    onToggleChrome: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val boundaryTransitionProgress = remember { Animatable(0f) }
    var boundaryTransition by remember { mutableStateOf<ReaderBoundaryPageTransition?>(null) }
    var currentPageLayoutResult by remember(bookId, chapterIndex) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    var lastSettledPage by remember(bookId, chapterIndex) {
        mutableStateOf<Int?>(null)
    }
    var pendingProgrammaticSettledPage by remember(bookId, chapterIndex) {
        mutableStateOf<Int?>(null)
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val density = LocalDensity.current
        val systemBarPadding = WindowInsets.safeDrawing.asPaddingValues()
        val systemTopInsetPx = with(density) { systemBarPadding.calculateTopPadding().roundToPx() }
        val systemBottomInsetPx = with(density) { systemBarPadding.calculateBottomPadding().roundToPx() }
        val textMeasurer = rememberTextMeasurer()
        val pageTextStyle = remember(fontSize, lineHeight, themePalette.content) {
            TextStyle(
                color = themePalette.content,
                fontSize = fontSize,
                lineHeight = lineHeight,
                textAlign = TextAlign.Start,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
        }
        val availableWidthPx = with(density) {
            (maxWidth - (ReaderHorizontalPadding * 2)).roundToPx()
        }
        val viewportMetrics = remember(
            maxHeight,
            systemTopInsetPx,
            systemBottomInsetPx,
        ) {
            ReaderPageViewportMetricsResolver.resolve(
                containerHeightPx = with(density) { maxHeight.roundToPx() },
                baseTopPaddingPx = with(density) { ReaderPageTopPadding.roundToPx() },
                baseBottomPaddingPx = with(density) { ReaderPageBottomPadding.roundToPx() },
                systemTopInsetPx = systemTopInsetPx,
                systemBottomInsetPx = systemBottomInsetPx,
            )
        }
        val pageTopPadding = with(density) { viewportMetrics.topPaddingPx.toDp() }
        val pageBottomPadding = with(density) { viewportMetrics.bottomPaddingPx.toDp() }
        val safePages = remember(
            content,
            availableWidthPx,
            viewportMetrics.availableHeightPx,
            pageTextStyle,
            textMeasurer,
        ) {
            paginatePageSlices(
                content = content,
                availableWidthPx = availableWidthPx,
                availableHeightPx = viewportMetrics.availableHeightPx,
                textMeasurer = textMeasurer,
                textStyle = pageTextStyle,
            )
        }
        val previousPages = remember(
            previousChapterContent,
            previousChapterIndex,
            availableWidthPx,
            viewportMetrics.availableHeightPx,
            pageTextStyle,
            textMeasurer,
        ) {
            paginatePageSlices(
                content = previousChapterContent,
                availableWidthPx = availableWidthPx,
                availableHeightPx = viewportMetrics.availableHeightPx,
                textMeasurer = textMeasurer,
                textStyle = pageTextStyle,
            )
        }
        val nextPages = remember(
            nextChapterContent,
            nextChapterIndex,
            availableWidthPx,
            viewportMetrics.availableHeightPx,
            pageTextStyle,
            textMeasurer,
        ) {
            paginatePageSlices(
                content = nextChapterContent,
                availableWidthPx = availableWidthPx,
                availableHeightPx = viewportMetrics.availableHeightPx,
                textMeasurer = textMeasurer,
                textStyle = pageTextStyle,
            )
        }
        val pagerState = rememberPagerState(
            pageCount = { safePages.size },
        )

        LaunchedEffect(bookId, chapterIndex, safePages) {
            onPagesChanged(safePages)
        }

        LaunchedEffect(bookId, chapterIndex, restoreCharOffset, restoreToLastPage, contentLoaded, safePages, restoredPosition) {
            if (!contentLoaded || restoredPosition) {
                return@LaunchedEffect
            }

            val targetPage = if (restoreToLastPage) {
                safePages.lastIndex
            } else {
                ReaderPageAnchorMapper.pageIndexForCharOffset(
                    pages = safePages,
                    charOffset = restoreCharOffset,
                )
            }
            pendingProgrammaticSettledPage = targetPage
            pagerState.scrollToPage(targetPage)
            onRestored(targetPage)
        }

        LaunchedEffect(bookId, chapterIndex, safePages, restoredPosition) {
            if (!restoredPosition) {
                return@LaunchedEffect
            }
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collect { settledPage ->
                    val previousSettledPage = lastSettledPage
                    val programmaticSettledPage = pendingProgrammaticSettledPage
                    if (
                        previousSettledPage != null &&
                        settledPage != previousSettledPage &&
                        programmaticSettledPage != settledPage
                    ) {
                        onManualFollowInterruption()
                    }
                    if (programmaticSettledPage == settledPage) {
                        pendingProgrammaticSettledPage = null
                    }
                    lastSettledPage = settledPage
                    onPageSettled(settledPage)
                }
        }

        LaunchedEffect(followTargetCharOffset, restoredPosition, safePages) {
            val targetCharOffset = followTargetCharOffset ?: return@LaunchedEffect
            if (!restoredPosition || safePages.isEmpty()) {
                return@LaunchedEffect
            }
            val targetPage = ReaderPageAnchorMapper.pageIndexForCharOffset(
                pages = safePages,
                charOffset = targetCharOffset,
            )
            if (pagerState.currentPage != targetPage) {
                pendingProgrammaticSettledPage = targetPage
                pagerState.scrollToPage(targetPage)
            }
        }

        LaunchedEffect(chapterIndex, contentLoaded, restoredPosition, boundaryTransition?.targetChapterIndex) {
            val activeTransition = boundaryTransition ?: return@LaunchedEffect
            if (activeTransition.targetChapterIndex == chapterIndex && contentLoaded && restoredPosition) {
                boundaryTransition = null
                boundaryTransitionProgress.snapTo(0f)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    bookId,
                    chapterIndex,
                    content,
                    safePages.size,
                    previousChapterIndex,
                    previousChapterContent,
                    nextChapterIndex,
                    nextChapterContent,
                    boundaryTransition,
                ) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            if (!enableTtsRestartGesture) {
                                return@detectTapGestures
                            }
                            val activePage = safePages.getOrNull(pagerState.currentPage) ?: return@detectTapGestures
                            val layoutResult = currentPageLayoutResult ?: return@detectTapGestures
                            val localPosition = Offset(
                                x = offset.x - with(density) { ReaderHorizontalPadding.toPx() },
                                y = offset.y - with(density) { pageTopPadding.toPx() },
                            )
                            if (
                                localPosition.x < 0f ||
                                localPosition.y < 0f ||
                                localPosition.x > layoutResult.size.width ||
                                localPosition.y > layoutResult.size.height
                            ) {
                                return@detectTapGestures
                            }
                            val localCharOffset = layoutResult.getOffsetForPosition(localPosition)
                            onRestartFromCharOffset(
                                activePage.startCharOffset + localCharOffset,
                            )
                        },
                    ) { offset ->
                        if (boundaryTransition != null) {
                            return@detectTapGestures
                        }
                        when (ReaderTapZone.resolve(offset.x, size.width.toFloat())) {
                            ReaderTapZone.PREVIOUS -> {
                                scope.launch {
                                    onManualFollowInterruption()
                                    if (pagerState.currentPage > 0) {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    } else {
                                        val previewText = previousPages.lastOrNull()?.text
                                        val sourceText = safePages.getOrNull(pagerState.currentPage)?.text.orEmpty()
                                        if (!previewText.isNullOrBlank() && previousChapterIndex != null) {
                                            boundaryTransition = ReaderBoundaryPageTransition(
                                                direction = ReaderPageBoundaryDirection.PREVIOUS,
                                                sourceText = sourceText,
                                                previewText = previewText,
                                                targetChapterIndex = previousChapterIndex,
                                            )
                                            boundaryTransitionProgress.snapTo(0f)
                                            boundaryTransitionProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(durationMillis = 180),
                                            )
                                        }
                                        onOpenPreviousBoundary()
                                    }
                                }
                            }

                            ReaderTapZone.NEXT -> {
                                scope.launch {
                                    onManualFollowInterruption()
                                    if (pagerState.currentPage < safePages.lastIndex) {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    } else {
                                        val previewText = nextPages.firstOrNull()?.text
                                        val sourceText = safePages.getOrNull(pagerState.currentPage)?.text.orEmpty()
                                        if (!previewText.isNullOrBlank() && nextChapterIndex != null) {
                                            boundaryTransition = ReaderBoundaryPageTransition(
                                                direction = ReaderPageBoundaryDirection.NEXT,
                                                sourceText = sourceText,
                                                previewText = previewText,
                                                targetChapterIndex = nextChapterIndex,
                                            )
                                            boundaryTransitionProgress.snapTo(0f)
                                            boundaryTransitionProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(durationMillis = 180),
                                            )
                                        }
                                        onOpenNextBoundary()
                                    }
                                }
                            }

                            ReaderTapZone.TOGGLE_CHROME -> onToggleChrome()
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = ReaderHorizontalPadding,
                            end = ReaderHorizontalPadding,
                            top = pageTopPadding,
                            bottom = pageBottomPadding,
                        ),
                ) {
                    Text(
                        text = safePages[pageIndex].annotatedPageText(
                            highlightRange = highlightRange,
                            highlightColor = themePalette.content.copy(alpha = 0.18f),
                        ),
                        style = pageTextStyle,
                        softWrap = true,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { layoutResult ->
                            if (pageIndex == pagerState.currentPage) {
                                currentPageLayoutResult = layoutResult
                            }
                        },
                    )
                }
            }

            boundaryTransition?.let { transition ->
                val containerWidthPx = with(density) { this@BoxWithConstraints.maxWidth.roundToPx() }
                val currentOffsetPx = when (transition.direction) {
                    ReaderPageBoundaryDirection.NEXT -> -containerWidthPx * boundaryTransitionProgress.value
                    ReaderPageBoundaryDirection.PREVIOUS -> containerWidthPx * boundaryTransitionProgress.value
                }
                val previewOffsetPx = when (transition.direction) {
                    ReaderPageBoundaryDirection.NEXT -> containerWidthPx * (1f - boundaryTransitionProgress.value)
                    ReaderPageBoundaryDirection.PREVIOUS -> -containerWidthPx * (1f - boundaryTransitionProgress.value)
                }
                ReaderBoundaryPageLayer(
                    text = transition.sourceText,
                    backgroundColor = themePalette.background,
                    textStyle = pageTextStyle,
                    topPadding = pageTopPadding,
                    bottomPadding = pageBottomPadding,
                    offsetPx = currentOffsetPx.roundToInt(),
                )
                ReaderBoundaryPageLayer(
                    text = transition.previewText,
                    backgroundColor = themePalette.background,
                    textStyle = pageTextStyle,
                    topPadding = pageTopPadding,
                    bottomPadding = pageBottomPadding,
                    offsetPx = previewOffsetPx.roundToInt(),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ReaderBoundaryPageLayer(
    text: String,
    backgroundColor: Color,
    textStyle: TextStyle,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    offsetPx: Int,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .offset { IntOffset(x = offsetPx, y = 0) }
            .background(backgroundColor)
            .padding(
                start = ReaderHorizontalPadding,
                end = ReaderHorizontalPadding,
                top = topPadding,
                bottom = bottomPadding,
            ),
    ) {
        Text(
            text = text,
            style = textStyle,
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun ReaderParagraphContent(
    text: String,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    highlightRange: ReaderTtsCharacterRange? = null,
    onLongPressCharOffset: ((Int) -> Unit)? = null,
) {
    val paragraphs = remember(text) { text.toDisplayParagraphs() }
    Column(
        verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
    ) {
        paragraphs.forEach { paragraph ->
            var textLayoutResult by remember(paragraph) {
                mutableStateOf<TextLayoutResult?>(null)
            }
            Text(
                text = paragraph.annotatedText(
                    highlightRange = highlightRange,
                    highlightColor = themePalette.content.copy(alpha = 0.18f),
                ),
                modifier = if (onLongPressCharOffset != null && paragraph.endCharOffset > paragraph.startCharOffset) {
                    Modifier.pointerInput(paragraph, onLongPressCharOffset) {
                        detectTapGestures(
                            onLongPress = { pressOffset ->
                                val layoutResult = textLayoutResult ?: return@detectTapGestures
                                val localCharOffset = layoutResult.getOffsetForPosition(pressOffset)
                                onLongPressCharOffset(
                                    paragraph.startCharOffset + localCharOffset,
                                )
                            },
                        )
                    }
                } else {
                    Modifier
                },
                color = themePalette.content,
                fontSize = fontSize,
                lineHeight = lineHeight,
                textAlign = TextAlign.Start,
                onTextLayout = { textLayoutResult = it },
            )
        }
    }
}

private fun String.toDisplayParagraphs(): List<ReaderDisplayParagraph> {
    if (isBlank()) {
        return listOf(
            ReaderDisplayParagraph(
                text = "当前章节暂无正文。",
                startCharOffset = 0,
                endCharOffset = 0,
            ),
        )
    }

    val paragraphs = mutableListOf<ReaderDisplayParagraph>()
    var lineStart = 0
    while (lineStart <= lastIndex) {
        val rawLineEnd = indexOf('\n', startIndex = lineStart)
            .takeIf { it >= 0 }
            ?: length
        val rawLine = substring(lineStart, rawLineEnd)
        val trimmedStart = rawLine.indexOfFirst { !it.isWhitespace() }
        val trimmedEnd = rawLine.indexOfLast { !it.isWhitespace() }
        if (trimmedStart >= 0 && trimmedEnd >= trimmedStart) {
            val startOffset = lineStart + trimmedStart
            val endOffset = lineStart + trimmedEnd + 1
            paragraphs += ReaderDisplayParagraph(
                text = substring(startOffset, endOffset),
                startCharOffset = startOffset,
                endCharOffset = endOffset,
            )
        }
        if (rawLineEnd >= length) {
            break
        }
        lineStart = rawLineEnd + 1
    }
    return if (paragraphs.isEmpty()) {
        listOf(
            ReaderDisplayParagraph(
                text = "当前章节暂无正文。",
                startCharOffset = 0,
                endCharOffset = 0,
            ),
        )
    } else {
        paragraphs
    }
}

private fun buildPendingRestartVisualRange(
    location: ReaderTextStartLocation,
    chapterText: String,
): ReaderTtsActiveVisualRange? {
    if (chapterText.isEmpty()) {
        return null
    }
    val startCharOffset = location.charOffset.coerceIn(0, chapterText.lastIndex)
    val endCharOffset = (startCharOffset + 8).coerceAtMost(chapterText.length)
        .coerceAtLeast(startCharOffset + 1)
    return ReaderTtsActiveVisualRange(
        chapterIndex = location.chapterIndex,
        startCharOffset = startCharOffset,
        endCharOffset = endCharOffset,
    )
}

private data class ReaderFeedChapterContent(
    val chapter: Chapter,
    val text: String,
)

private data class ReaderDisplayParagraph(
    val text: String,
    val startCharOffset: Int,
    val endCharOffset: Int,
)

private data class ReaderLoadedChapterContent(
    val chapterIndex: Int,
    val text: String,
)

private data class ReaderBoundaryPageTransition(
    val direction: ReaderPageBoundaryDirection,
    val sourceText: String,
    val previewText: String,
    val targetChapterIndex: Int,
)

private enum class ReaderPageBoundaryDirection {
    PREVIOUS,
    NEXT,
}

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

private fun paginatePageSlices(
    content: String,
    availableWidthPx: Int,
    availableHeightPx: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
): List<ReaderPageSlice> {
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
    )
    if (lines.isEmpty()) {
        return listOf(
            ReaderPageSlice(
                startCharOffset = 0,
                endCharOffset = 0,
                text = "当前章节暂无正文。",
            ),
        )
    }

    val pages = mutableListOf<ReaderPageSlice>()
    var lineIndex = 0
    while (lineIndex < lines.size) {
        val pageStartLine = lines[lineIndex]
        val pageTop = pageStartLine.topPx
        val pageBottomLimit = pageTop + availableHeightPx.coerceAtLeast(1).toFloat()

        var lastVisibleLineIndex = lineIndex
        while (
            lastVisibleLineIndex < lines.lastIndex &&
            lines[lastVisibleLineIndex + 1].bottomPx <= pageBottomLimit
        ) {
            lastVisibleLineIndex += 1
        }

        var candidateLineIndex = lastVisibleLineIndex
        var candidatePage = buildReaderPageSlice(
            content = content,
            lines = lines,
            startLineIndex = lineIndex,
            endLineIndex = candidateLineIndex,
        )
        while (
            candidateLineIndex > lineIndex &&
            !pageTextFitsViewport(
                text = candidatePage.text,
                availableWidthPx = availableWidthPx,
                availableHeightPx = availableHeightPx,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
            )
        ) {
            candidateLineIndex -= 1
            candidatePage = buildReaderPageSlice(
                content = content,
                lines = lines,
                startLineIndex = lineIndex,
                endLineIndex = candidateLineIndex,
            )
        }

        pages += candidatePage
        lineIndex = candidateLineIndex + 1
    }

    return pages
}

private fun buildReaderPageSlice(
    content: String,
    lines: List<ReaderPageLine>,
    startLineIndex: Int,
    endLineIndex: Int,
): ReaderPageSlice {
    val startCharOffset = lines[startLineIndex].startCharOffset
    val endCharOffset = lines[endLineIndex].endCharOffset
        .coerceAtLeast((startCharOffset + 1).coerceAtMost(content.length))
    val rawText = content.substring(startCharOffset, endCharOffset)
    return ReaderPageSlice(
        startCharOffset = startCharOffset,
        endCharOffset = endCharOffset,
        text = rawText.trim('\n').ifBlank { rawText.ifBlank { "当前章节暂无正文。" } },
    )
}

private fun pageTextFitsViewport(
    text: String,
    availableWidthPx: Int,
    availableHeightPx: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
): Boolean {
    if (text.isBlank()) {
        return true
    }
    val layoutResult = textMeasurer.measure(
        text = AnnotatedString(text),
        style = textStyle,
        overflow = TextOverflow.Clip,
        softWrap = true,
        constraints = Constraints(
            maxWidth = availableWidthPx.coerceAtLeast(1),
        ),
    )
    return layoutResult.size.height <= availableHeightPx
}

private fun ReaderDisplayParagraph.annotatedText(
    highlightRange: ReaderTtsCharacterRange?,
    highlightColor: Color,
): AnnotatedString {
    if (highlightRange == null || endCharOffset <= startCharOffset) {
        return AnnotatedString(text)
    }
    val localHighlightStart = maxOf(highlightRange.startCharOffset, startCharOffset) - startCharOffset
    val localHighlightEnd = minOf(highlightRange.endCharOffset, endCharOffset) - startCharOffset
    if (localHighlightStart >= localHighlightEnd) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        append(text)
        addStyle(
            style = SpanStyle(background = highlightColor),
            start = localHighlightStart,
            end = localHighlightEnd,
        )
    }
}

private fun ReaderPageSlice.annotatedPageText(
    highlightRange: ReaderTtsCharacterRange?,
    highlightColor: Color,
): AnnotatedString {
    if (highlightRange == null || endCharOffset <= startCharOffset) {
        return AnnotatedString(text)
    }
    val localHighlightStart = maxOf(highlightRange.startCharOffset, startCharOffset) - startCharOffset
    val localHighlightEnd = minOf(highlightRange.endCharOffset, endCharOffset) - startCharOffset
    if (localHighlightStart >= localHighlightEnd) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        append(text)
        addStyle(
            style = SpanStyle(background = highlightColor),
            start = localHighlightStart,
            end = localHighlightEnd,
        )
    }
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
