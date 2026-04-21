package com.longerlsx.storyapp

import android.app.Application
import com.longerlsx.storyapp.data.book.FileAnchorStore
import com.longerlsx.storyapp.data.book.ImportCoordinator
import com.longerlsx.storyapp.data.book.ImportedBookStorage
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.book.TextContentLoader
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import kotlinx.coroutines.runBlocking

class StoryApplication : Application() {
    val importedBookStorage by lazy {
        ImportedBookStorage(filesDir)
    }

    val textContentLoader by lazy {
        TextContentLoader()
    }

    val anchorStore by lazy {
        FileAnchorStore(filesDir)
    }

    val readerSettingsStore by lazy {
        ReaderSettingsStore(filesDir)
    }

    val bookRepository by lazy {
        InMemoryBookRepository(anchorStore = anchorStore).also { repository ->
            runBlocking {
                repository.hydrateFromStorage(
                    snapshots = importedBookStorage.restoreCatalog(),
                    textContentLoader = textContentLoader,
                )
                repository.hydrateProgress(
                    progressItems = anchorStore.loadAll().sortedBy { it.updatedAt },
                )
            }
        }
    }

    val importCoordinator by lazy {
        ImportCoordinator(
            repository = bookRepository,
            storage = importedBookStorage,
            textContentLoader = textContentLoader,
        )
    }
}
