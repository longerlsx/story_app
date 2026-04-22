package com.longerlsx.storyapp

import android.app.Application
import androidx.core.content.ContextCompat
import com.longerlsx.storyapp.data.book.FileAnchorStore
import com.longerlsx.storyapp.data.book.ImportCoordinator
import com.longerlsx.storyapp.data.book.ImportedBookStorage
import com.longerlsx.storyapp.data.book.InMemoryBookRepository
import com.longerlsx.storyapp.data.book.TextContentLoader
import com.longerlsx.storyapp.data.reader.ReaderSettingsStore
import com.longerlsx.storyapp.feature.reader.tts.AndroidReaderTtsEngine
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsController
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsEngine
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsIntentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class StoryApplication : Application() {
    private var readerTtsEngineFactoryForTests: ((ReaderTtsEngine.Callback) -> ReaderTtsEngine)? = null
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var preloadVoicesJob: kotlinx.coroutines.Job? = null

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
                runCatching {
                    startService(ReaderTtsIntentFactory.stopService(this))
                }
            },
            sendResumeCommand = {
                runCatching {
                    startService(ReaderTtsIntentFactory.resumeService(this))
                }
            },
            sendSettingsCommand = { settings ->
                runCatching {
                    startService(ReaderTtsIntentFactory.updateSettingsService(this, settings))
                }
            },
            sendRestartCommand = { request ->
                runCatching {
                    startService(ReaderTtsIntentFactory.restartService(this, request))
                }
            },
        )
    }

    fun createReaderTtsEngine(
        callback: ReaderTtsEngine.Callback,
    ): ReaderTtsEngine {
        return readerTtsEngineFactoryForTests?.invoke(callback)
            ?: AndroidReaderTtsEngine(this, callback)
    }

    fun setReaderTtsEngineFactoryForTests(
        factory: ((ReaderTtsEngine.Callback) -> ReaderTtsEngine)?,
    ) {
        readerTtsEngineFactoryForTests = factory
    }

    fun preloadReaderTtsVoices() {
        if (readerTtsController.runtimeState.value.availableVoices.isNotEmpty()) {
            return
        }
        if (preloadVoicesJob?.isActive == true) {
            return
        }
        preloadVoicesJob = applicationScope.launch {
            val preloadEngine = createReaderTtsEngine(NoopReaderTtsCallback)
            val voices = preloadEngine.initialize().getOrNull().orEmpty()
            if (voices.isNotEmpty()) {
                readerTtsController.updateAvailableVoices(voices)
            }
            preloadEngine.shutdown()
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
