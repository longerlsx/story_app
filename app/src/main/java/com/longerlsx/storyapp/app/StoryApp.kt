package com.longerlsx.storyapp.app

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.longerlsx.storyapp.LibraryInitializationState
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.feature.bookshelf.BookshelfScreen
import com.longerlsx.storyapp.feature.importer.ExternalImportHandler
import com.longerlsx.storyapp.feature.importer.ExternalImportPayload
import com.longerlsx.storyapp.feature.reader.ReaderScreen
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsIntentFactory
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsNavigationPolicy
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsStartRequest
import com.longerlsx.storyapp.feature.source.SourceEntryScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class InitialReaderRoute(val bookId: String?)

@Composable
fun StoryApp(
    externalIntent: Intent? = null,
    onExternalIntentConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as StoryApplication
    val initialization by application.libraryInitialization.collectAsState()

    when (val state = initialization) {
        LibraryInitializationState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text("正在载入书架…")
        }

        is LibraryInitializationState.Failed -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.message)
            Button(onClick = application::retryLibraryInitialization) { Text("重试") }
        }

        is LibraryInitializationState.Ready -> ReadyStoryApp(
            application = application,
            initialization = state,
            externalIntent = externalIntent,
            onExternalIntentConsumed = onExternalIntentConsumed,
        )
    }
}

@Composable
private fun ReadyStoryApp(
    application: StoryApplication,
    initialization: LibraryInitializationState.Ready,
    externalIntent: Intent?,
    onExternalIntentConsumed: () -> Unit,
) {
    val initialRoute by produceState<InitialReaderRoute?>(initialValue = null, key1 = application) {
        val bookId = withContext(Dispatchers.IO) {
            try {
                application.anchorStore.getLastOpenedBookId()
                    ?.takeIf { application.bookRepository.getBook(it) != null }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        value = InitialReaderRoute(bookId)
    }
    val route = initialRoute
    if (route == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val context = LocalContext.current
    val ttsController = application.readerTtsController
    val appState = rememberStoryAppState(
        initialBookId = route.bookId,
    )
    val books by application.bookRepository.observeBookshelf().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val importMutex = remember { Mutex() }
    var importing by remember { mutableStateOf(false) }
    var pendingListeningOpenRequest by remember { mutableStateOf<ReaderTtsStartRequest?>(null) }
    val settingsWriteError by application.readerSettingsStore.writeError.collectAsState()
    val progressWriteError by application.progressWriteError.collectAsState()
    fun openReaderWithTtsGuard(bookId: String) {
        pendingListeningOpenRequest = null
        if (
            ReaderTtsNavigationPolicy.shouldStopForOpenReader(
                playbackState = ttsController.playbackState,
                activeBookId = ttsController.currentBookId,
                nextBookId = bookId,
            )
        ) {
            ttsController.stopByNavigation()
        }
        appState.openReader(bookId)
    }

    suspend fun importPreparedPayload(
        sourceType: ImportSourceType,
        prepare: suspend () -> Result<ExternalImportPayload>?,
    ) {
        importMutex.withLock {
            importing = true
            try {
                application.awaitLibraryReady()
                val payload = prepare()?.getOrThrow() ?: return@withLock
                val result = application.importCoordinator.importTxt(
                    fileName = payload.fileName,
                    bytes = payload.bytes,
                    sourceType = sourceType,
                )
                openReaderWithTtsGuard(result.book.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                scope.launch {
                    snackbarHost.showSnackbar(
                        message = failure.message ?: "导入失败，请重新选择 TXT 文件。",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                }
            } finally {
                importing = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importPreparedPayload(ImportSourceType.LOCAL_FILE) {
                ExternalImportHandler.prepareImport(context, uri)
            }
        }
    }
    LaunchedEffect(externalIntent) {
        val listeningRequest = ReaderTtsIntentFactory.extractOpenReaderRequest(externalIntent)
        if (listeningRequest != null) {
            openReaderWithTtsGuard(listeningRequest.bookId)
            pendingListeningOpenRequest = listeningRequest
            onExternalIntentConsumed()
            return@LaunchedEffect
        }
        val notificationBookId = ReaderTtsIntentFactory.extractOpenReaderBookId(externalIntent)
        if (notificationBookId != null) {
            openReaderWithTtsGuard(notificationBookId)
            onExternalIntentConsumed()
            return@LaunchedEffect
        }

        if (externalIntent != null) {
            importPreparedPayload(ImportSourceType.EXTERNAL_INTENT) {
                ExternalImportHandler.prepareImport(context, externalIntent)
            }
            onExternalIntentConsumed()
        }
    }

    LaunchedEffect(initialization.issues) {
        if (initialization.issues.isNotEmpty()) {
            snackbarHost.showSnackbar(
                message = "${initialization.issues.size} 本书未能载入：" +
                    initialization.issues.joinToString("；") { "${it.bookId}：${it.message}" },
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
        }
    }
    LaunchedEffect(settingsWriteError) {
        settingsWriteError?.let {
            snackbarHost.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(progressWriteError) {
        progressWriteError?.let {
            snackbarHost.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (appState.currentScreen) {
            AppScreen.BOOKSHELF -> BookshelfScreen(
                books = books,
                onImportTxt = {
                    if (!importing) importLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                },
                onOpenSourceEntry = appState::openSourceEntry,
                onOpenBook = ::openReaderWithTtsGuard,
            )

            AppScreen.READER -> ReaderScreen(
                bookId = requireNotNull(appState.selectedBookId),
                repository = application.bookRepository,
                settingsStore = application.readerSettingsStore,
                ttsController = ttsController,
                onBack = appState::openBookshelf,
                listeningOpenRequest = pendingListeningOpenRequest,
                onListeningOpenHandled = { pendingListeningOpenRequest = null },
            )

            AppScreen.SOURCE_ENTRY -> SourceEntryScreen(
                onBack = appState::openBookshelf,
            )
        }
        if (importing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}
