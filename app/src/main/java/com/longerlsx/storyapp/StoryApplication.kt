package com.longerlsx.storyapp

import android.app.Application
import androidx.core.content.ContextCompat
import com.longerlsx.storyapp.core.model.ReadingProgress
import com.longerlsx.storyapp.data.book.BookRepository
import com.longerlsx.storyapp.data.book.FileAnchorStore
import com.longerlsx.storyapp.data.book.BookStorageIssue
import com.longerlsx.storyapp.data.book.ImportCoordinator
import com.longerlsx.storyapp.data.book.ImportedBookStorage
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.book.TextContentLoader
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import com.longerlsx.storyapp.data.reader.ListeningProgressStore
import com.longerlsx.storyapp.feature.reader.tts.OfflineReaderTtsEngine
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsController
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsEngine
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsIntentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LibraryInitializationState {
    data object Loading : LibraryInitializationState
    data class Ready(
        val lastOpenedBookId: String?,
        val issues: List<BookStorageIssue>,
    ) : LibraryInitializationState
    data class Failed(val message: String) : LibraryInitializationState
}

class StoryApplication : Application() {
    private var readerTtsEngineFactoryForTests: ((ReaderTtsEngine.Callback) -> ReaderTtsEngine)? = null
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var preloadVoicesJob: kotlinx.coroutines.Job? = null
    private val initializationLock = Any()
    private var initializationTask: Deferred<LibraryInitializationState>? = null
    private val mutableLibraryInitialization = MutableStateFlow<LibraryInitializationState>(
        LibraryInitializationState.Loading,
    )
    val libraryInitialization = mutableLibraryInitialization.asStateFlow()
    private val mutableProgressWriteError = MutableStateFlow<String?>(null)
    val progressWriteError = mutableProgressWriteError.asStateFlow()
    private val progressPersistenceMutex = Mutex()

    val importedBookStorage by lazy {
        ImportedBookStorage(filesDir)
    }

    val textContentLoader by lazy {
        TextContentLoader()
    }

    val anchorStore by lazy {
        FileAnchorStore(filesDir)
    }

    val listeningProgressStore by lazy { ListeningProgressStore(filesDir) }

    val readerSettingsStore by lazy {
        ReaderSettingsStore(
            filesDir,
            writeScope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.IO),
        )
    }

    val bookRepository by lazy {
        InMemoryBookRepository(anchorStore = anchorStore)
    }

    override fun onCreate() {
        super.onCreate()
        getInitializationTask()
    }

    suspend fun awaitLibraryReady(): LibraryInitializationState = getInitializationTask().await()

    fun persistReadingProgress(
        progress: ReadingProgress,
        repository: BookRepository = bookRepository,
    ): Job = applicationScope.launch {
        progressPersistenceMutex.withLock {
            try {
                val isApplicationRepository = repository === bookRepository
                if (isApplicationRepository && awaitLibraryReady() !is LibraryInitializationState.Ready) {
                    mutableProgressWriteError.value = "阅读位置暂时无法保存，请稍后重试。"
                    return@withLock
                }
                if (isApplicationRepository) readerSettingsStore.awaitPendingWrites()
                repository.saveReadingProgress(progress)
                if (repository.getReadingProgress(progress.bookId) == progress) {
                    mutableProgressWriteError.value = null
                    val ready = mutableLibraryInitialization.value as? LibraryInitializationState.Ready
                    if (isApplicationRepository && ready != null) {
                        mutableLibraryInitialization.value = ready.copy(lastOpenedBookId = progress.bookId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableProgressWriteError.value = "阅读位置保存失败，请稍后重试。"
            }
        }
    }

    fun retryLibraryInitialization() {
        synchronized(initializationLock) {
            if (mutableLibraryInitialization.value !is LibraryInitializationState.Failed) return
            initializationTask = null
            mutableLibraryInitialization.value = LibraryInitializationState.Loading
            getInitializationTask()
        }
    }

    private fun getInitializationTask(): Deferred<LibraryInitializationState> = synchronized(initializationLock) {
        initializationTask ?: applicationScope.async(Dispatchers.IO) {
            val result = try {
                val restored = importedBookStorage.restoreCatalogWithIssues()
                bookRepository.hydrateFromStorage(restored.snapshots, textContentLoader)
                bookRepository.hydrateProgress(anchorStore.loadAll())
                readerSettingsStore.load()
                val lastOpenedBookId = anchorStore.getLastOpenedBookId()
                    ?.takeIf { bookRepository.getBook(it) != null }
                LibraryInitializationState.Ready(lastOpenedBookId, restored.issues)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LibraryInitializationState.Failed("书架暂时无法载入，请重试。原有文件会保留。")
            }
            mutableLibraryInitialization.value = result
            result
        }.also { initializationTask = it }
    }

    val importCoordinator by lazy {
        ImportCoordinator(
            repository = bookRepository,
            storage = importedBookStorage,
            textContentLoader = textContentLoader,
        )
    }

    val readerTtsController by lazy {
        ReaderTtsController(
            launchForegroundService = { request ->
                runCatching {
                    ContextCompat.startForegroundService(
                        this,
                        ReaderTtsIntentFactory.startService(this, request),
                    )
                }.isSuccess
            },
            sendStopCommand = {
                startService(ReaderTtsIntentFactory.stopService(this))
            },
            sendResumeCommand = {
                ContextCompat.startForegroundService(this, ReaderTtsIntentFactory.resumeService(this))
            },
            sendSettingsCommand = { settings ->
                startService(ReaderTtsIntentFactory.updateSettingsService(this, settings))
            },
            sendRestartCommand = { request ->
                ContextCompat.startForegroundService(this, ReaderTtsIntentFactory.restartService(this, request))
            },
            sendPauseCommand = {
                startService(ReaderTtsIntentFactory.pauseService(this))
            },
            sendVoicePreviewCommand = { settings ->
                ContextCompat.startForegroundService(this, ReaderTtsIntentFactory.previewService(this, settings))
            },
        )
    }

    fun createReaderTtsEngine(
        callback: ReaderTtsEngine.Callback,
    ): ReaderTtsEngine {
        return readerTtsEngineFactoryForTests?.invoke(callback)
            ?: OfflineReaderTtsEngine(this, callback)
    }

    fun setReaderTtsEngineFactoryForTests(
        factory: ((ReaderTtsEngine.Callback) -> ReaderTtsEngine)?,
    ) {
        readerTtsEngineFactoryForTests = factory
    }

    fun preloadReaderTtsVoices() {
        if (readerTtsEngineFactoryForTests == null) {
            readerTtsController.updateAvailableVoicesResult(Result.success(OfflineReaderTtsEngine.voices))
            return
        }
        if (readerTtsController.runtimeState.value.availableVoicesLoaded) {
            return
        }
        if (preloadVoicesJob?.isActive == true) {
            return
        }
        preloadVoicesJob = applicationScope.launch {
            var preloadEngine: ReaderTtsEngine? = null
            try {
                preloadEngine = createReaderTtsEngine(NoopReaderTtsCallback)
                readerTtsController.updateAvailableVoicesResult(preloadEngine.initialize())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                readerTtsController.updateAvailableVoicesResult(Result.failure(failure))
            } finally {
                runCatching { preloadEngine?.shutdown() }
            }
        }
    }

    fun resetReaderTtsRuntimeForTests() {
        readerTtsController.resetForTests()
        readerTtsEngineFactoryForTests = null
        preloadVoicesJob?.cancel()
        preloadVoicesJob = null
        runCatching {
            stopService(ReaderTtsIntentFactory.stopService(this))
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    private object NoopReaderTtsCallback : ReaderTtsEngine.Callback {
        override fun onUtteranceStarted(utteranceId: String) = Unit

        override fun onUtteranceRangeStart(
            utteranceId: String,
            start: Int,
            end: Int,
        ) = Unit

        override fun onUtteranceCompleted(utteranceId: String) = Unit

        override fun onUtteranceError(
            utteranceId: String,
            message: String,
        ) = Unit
    }
}
