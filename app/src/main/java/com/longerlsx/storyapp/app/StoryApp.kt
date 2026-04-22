package com.longerlsx.storyapp.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.feature.bookshelf.BookshelfScreen
import com.longerlsx.storyapp.feature.importer.ExternalImportHandler
import com.longerlsx.storyapp.feature.reader.ReaderScreen
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsIntentFactory
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsNavigationPolicy
import com.longerlsx.storyapp.feature.source.SourceEntryScreen
import kotlinx.coroutines.launch

@Composable
fun StoryApp(
    externalIntent: Intent? = null,
    onExternalIntentConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as StoryApplication
    val ttsController = application.readerTtsController
    val appState = rememberStoryAppState(
        initialBookId = application.anchorStore.getLastOpenedBookId(),
    )
    val books by application.bookRepository.observeBookshelf().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    fun openReaderWithTtsGuard(bookId: String) {
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

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val fileName = context.resolveDisplayName(uri)
            val result = application.importCoordinator.importTxt(
                fileName = fileName,
                bytes = bytes,
                sourceType = ImportSourceType.LOCAL_FILE,
            )
            openReaderWithTtsGuard(result.book.id)
        }
    }
    LaunchedEffect(externalIntent) {
        val notificationBookId = ReaderTtsIntentFactory.extractOpenReaderBookId(externalIntent)
        if (notificationBookId != null) {
            openReaderWithTtsGuard(notificationBookId)
            onExternalIntentConsumed()
            return@LaunchedEffect
        }

        val payload = ExternalImportHandler.extractPayload(
            context = context,
            intent = externalIntent,
        ) ?: run {
            if (externalIntent != null) {
                onExternalIntentConsumed()
            }
            return@LaunchedEffect
        }

        val result = application.importCoordinator.importTxt(
            fileName = payload.fileName,
            bytes = payload.bytes,
            sourceType = ImportSourceType.EXTERNAL_INTENT,
        )
        openReaderWithTtsGuard(result.book.id)
        onExternalIntentConsumed()
    }

    when (appState.currentScreen) {
        AppScreen.BOOKSHELF -> BookshelfScreen(
            books = books,
            onImportTxt = {
                importLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
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
        )

        AppScreen.SOURCE_ENTRY -> SourceEntryScreen(
            onBack = appState::openBookshelf,
        )
    }
}

private fun Context.resolveDisplayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                return cursor.getString(nameIndex)
            }
        }
    }

    return uri.lastPathSegment ?: "imported.txt"
}
