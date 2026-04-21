package com.longerlsx.storyapp.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.Constraints
import com.longerlsx.storyapp.core.model.Book
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReaderAppearanceMode
import com.longerlsx.storyapp.core.model.ReaderSettings
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.data.book.BookRepository
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
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
    onBack: () -> Unit,
) {
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
    var readerSettings by remember {
        mutableStateOf(settingsStore.load())
    }
    var restoreToLastPageOnOpen by remember(state.book.id) {
        mutableStateOf(false)
    }
    var restoredPosition by remember(state.book.id, readerSettings.readingMode) {
        mutableStateOf(false)
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
    ) {
        selectedChapterIndex = chapterIndex
        pendingRestoreCharOffset = restoreCharOffset
        currentPageIndex = 0
        restoredPosition = false
        restoreToLastPageOnOpen = restoreToLastPage
        chromeMode = nextChromeMode
    }

    fun openAdjacentChapter(
        delta: Int,
        nextChromeMode: ReaderChromeMode,
    ) {
        val chapter = state.chapters.getOrNull(selectedChapterPosition + delta) ?: return
        openChapter(
            chapterIndex = chapter.chapterIndex,
            nextChromeMode = nextChromeMode,
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

    LaunchedEffect(chromeMode) {
        if (chromeMode == ReaderChromeMode.CHROME_VISIBLE) {
            delay(3_000)
            if (chromeMode == ReaderChromeMode.CHROME_VISIBLE) {
                chromeMode = ReaderChromeMode.READING_ONLY
            }
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
        if (ReaderRestorePolicy.shouldBootstrapProgress(pendingRestoreCharOffset)) {
            saveScrollProgress()
        }
    }

    LaunchedEffect(
        state.book.id,
        scrollFeed,
        restoredPosition,
        readerSettings.readingMode,
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
                if (!isScrolling) {
                    saveScrollProgress()
                }
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
            if (event == Lifecycle.Event.ON_STOP && restoredPosition) {
                scope.launch {
                    saveCurrentProgress()
                }
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
                onCenterTap = {
                    chromeMode = ReaderChromeStateReducer.onCenterTap(chromeMode)
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
                onPagesChanged = { currentPages = it },
                onRestored = { restoredPage ->
                    currentPageIndex = restoredPage
                    restoredPosition = true
                    restoreToLastPageOnOpen = false
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
                    val boundaryPreviousChapter = state.chapters.getOrNull(selectedChapterPosition - 1) ?: return@PageReaderContent
                    openChapter(
                        chapterIndex = boundaryPreviousChapter.chapterIndex,
                        nextChromeMode = chromeMode,
                        restoreToLastPage = true,
                    )
                },
                onOpenNextBoundary = {
                    openAdjacentChapter(1, chromeMode)
                },
                onToggleChrome = {
                    chromeMode = ReaderChromeStateReducer.onCenterTap(chromeMode)
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

        if (chromeMode != ReaderChromeMode.READING_ONLY) {
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
                    onOpenPreviousChapter = {
                        openAdjacentChapter(
                            delta = -1,
                            nextChromeMode = ReaderChromeStateReducer.onChapterStep(chromeMode),
                        )
                    },
                    onOpenNextChapter = {
                        openAdjacentChapter(
                            delta = 1,
                            nextChromeMode = ReaderChromeStateReducer.onChapterStep(chromeMode),
                        )
                    },
                    onOpenToc = {
                        chromeMode = ReaderChromeStateReducer.onOpenDirectory(chromeMode)
                    },
                    onToggleAppearanceMode = ::toggleAppearanceMode,
                    onOpenSettings = {
                        chromeMode = ReaderChromeStateReducer.onOpenSettings(chromeMode)
                    },
                    expandedContent = when (chromeMode) {
                        ReaderChromeMode.SETTINGS_EXPANDED -> {
                            {
                                ReaderSettingsSheet(
                                    settings = readerSettings,
                                    themePalette = themePalette,
                                    onUpdateSettings = ::updateReaderSettings,
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
    author: String?,
    themePalette: ReaderThemePalette,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    listState: LazyListState,
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
            pagerState.scrollToPage(targetPage)
            onRestored(targetPage)
        }

        LaunchedEffect(bookId, chapterIndex, safePages, restoredPosition) {
            if (!restoredPosition) {
                return@LaunchedEffect
            }
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collect(onPageSettled)
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
                    detectTapGestures { offset ->
                        if (boundaryTransition != null) {
                            return@detectTapGestures
                        }
                        when (ReaderTapZone.resolve(offset.x, size.width.toFloat())) {
                            ReaderTapZone.PREVIOUS -> {
                                scope.launch {
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
                        text = safePages[pageIndex].text,
                        style = pageTextStyle,
                        softWrap = true,
                        overflow = TextOverflow.Clip,
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
) {
    val paragraphs = remember(text) { text.toDisplayParagraphs() }
    Column(
        verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
    ) {
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                color = themePalette.content,
                fontSize = fontSize,
                lineHeight = lineHeight,
                textAlign = TextAlign.Start,
            )
        }
    }
}

private fun String.toDisplayParagraphs(): List<String> {
    val parts = lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    return if (parts.isEmpty()) {
        listOf("当前章节暂无正文。")
    } else {
        parts
    }
}

private data class ReaderFeedChapterContent(
    val chapter: Chapter,
    val text: String,
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
