package com.longerlsx.storyapp.feature.reader.tts

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.longerlsx.storyapp.LibraryInitializationState
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ListeningProgress
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/** A session owns one chapter queue, at most one prepared chapter, and one audio engine. */
class ReaderTtsService : Service(), ReaderTtsEngine.Callback {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storyApplication by lazy { applicationContext as StoryApplication }
    private val controller by lazy { storyApplication.readerTtsController }
    private val repository by lazy { storyApplication.bookRepository }
    private val settingsStore by lazy { storyApplication.readerSettingsStore }
    private val progressStore by lazy { storyApplication.listeningProgressStore }
    private val notificationFactory by lazy { ReaderTtsNotificationFactory(this) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "storyapp:listening")
            .apply { setReferenceCounted(false) }
    }
    private lateinit var mediaSession: MediaSession
    private val audioFocusManager by lazy {
        ReaderTtsAudioFocusManager(
            onPauseForFocusLoss = { pauseForAudioFocusLoss(it) },
            onResumeAfterFocusGain = {
                if (controller.playbackState == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS) {
                    if (!resumeRetainedAudioAfterFocus()) {
                        Log.i(TAG, "focusRestore retained=false")
                        runPlaybackOperation { resumePlayback(fromAudioFocus = true, token = it) }
                    }
                }
            },
            canAutoResume = { controller.playbackState.isSpeakingSession() && !controller.runtimeState.value.isVoicePreviewing },
        )
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseByUserAction()
        }
    }

    private var engine: ReaderTtsEngine? = null
    private var engineInitialization: Deferred<Result<List<ReaderTtsVoiceOption>>>? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focusEpoch = 0L
    private var activeStartRequest: ReaderTtsStartRequest? = null
    private var activeQueue: List<QueuedSegment> = emptyList()
    private var activeQueueIndex = 0
    private var nextChapter: Deferred<List<QueuedSegment>>? = null
    private var activeUtteranceId: String? = null
    private var activeUtteranceStarted = false
    private var focusPauseGate: CompletableDeferred<Unit>? = null
    private var pausedResumeLocation: ReaderTextStartLocation? = null
    private var activeChapters: List<Chapter> = emptyList()
    private var activeSettings = ReaderTtsSettings()
    private var remainingTimerMillis: Long? = null
    private var countdownStartedAtMillis: Long? = null
    private var countdownBaseRemainingMillis: Long? = null
    private var timerTickerJob: Job? = null
    private var preparationJob: Job? = null
    private var utteranceWatchdog: Job? = null
    private var utteranceTimeout: UtteranceTimeout? = null
    private var utteranceWatchdogStartedAt = 0L
    private var playbackToken = 0L
    private var utteranceSequence = 0L
    private var foreground = false
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession(this, "StoryAppListening").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    val state = this@ReaderTtsService.controller.runtimeState.value
                    if (state.isVoicePreviewing ||
                        (state.playbackState.isSpeakingSession() && activeStartRequest != null)) return
                    promoteToForeground()
                    runPlaybackOperation { resumePlayback(fromAudioFocus = false, token = it) }
                }
                override fun onPause() = pauseByUserAction()
                override fun onStop() {
                    this@ReaderTtsService.controller.markStoppedByUser()
                    stopPlaybackService()
                }
                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == ReaderTtsIntentFactory.ACTION_STOP) onStop()
                    else super.onCustomAction(action, extras)
                }
            })
            isActive = true
        }
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ReaderTtsIntentFactory.ACTION_START, ReaderTtsIntentFactory.ACTION_RESTART -> {
                    // Foreground promotion must precede storage/model suspension, including cold resume.
                    promoteToForeground()
                    val request = ReaderTtsIntentFactory.extractStartRequest(intent)
                        ?: ReaderTtsIntentFactory.extractRestartRequest(intent)
                    requireNotNull(request) { "朗读请求缺少必要参数" }
                    val keepTimer = intent.action == ReaderTtsIntentFactory.ACTION_RESTART && activeStartRequest != null
                    runPlaybackOperation { startPlayback(request, it, keepTimer) }
                }
                ReaderTtsIntentFactory.ACTION_RESUME -> {
                    promoteToForeground()
                    if (!controller.runtimeState.value.isVoicePreviewing &&
                        (!controller.playbackState.isSpeakingSession() || activeStartRequest == null)) {
                        runPlaybackOperation { resumePlayback(fromAudioFocus = false, token = it) }
                    }
                }
                ReaderTtsIntentFactory.ACTION_PAUSE -> pauseByUserAction()
                ReaderTtsIntentFactory.ACTION_STOP -> {
                    controller.markStoppedByUser()
                    stopPlaybackService()
                }
                ReaderTtsIntentFactory.ACTION_UPDATE_SETTINGS -> {
                    requireNotNull(ReaderTtsIntentFactory.extractSettings(intent)).let(::applyRuntimeSettings)
                }
                ReaderTtsIntentFactory.ACTION_PREVIEW -> {
                    promoteToForeground()
                    val settings = requireNotNull(ReaderTtsIntentFactory.extractSettings(intent))
                    pauseByUserAction()
                    runPlaybackOperation { previewVoice(settings, it) }
                }
                else -> if (activeStartRequest == null) stopSelf()
            }
        } catch (failure: Exception) {
            failPlayback(failure.message ?: "无法处理朗读操作，请重试")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        invalidatePlayback()
        timerTickerJob?.cancel()
        serviceScope.cancel()
        engine?.shutdown()
        engine = null
        releaseWakeLock()
        audioFocusManager.clearAutoResume()
        abandonAudioFocus()
        unregisterReceiver(noisyReceiver)
        mediaSession.release()
        controller.onServiceDisconnected()
        super.onDestroy()
    }

    /** Cancelling display/loading never lets an old completion mutate the next session. */
    private fun runPlaybackOperation(operation: suspend (Long) -> Unit) {
        if (activeStartRequest != null) freezeTimerBudget()
        invalidatePlayback()
        val token = playbackToken
        preparationJob = serviceScope.launch {
            try {
                acquireWakeLock()
                withTimeout(PREPARATION_TIMEOUT_MILLIS) { operation(token) }
            } catch (cancelled: CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException && isCurrent(token)) {
                    failPlayback("声音或正文准备超时，请重试")
                }
            } catch (failure: Exception) {
                if (isCurrent(token)) failPlayback(failure.message ?: "朗读失败，请重试")
            }
        }
    }

    private suspend fun startPlayback(request: ReaderTtsStartRequest, token: Long, keepTimer: Boolean) {
        activeStartRequest = request
        pausedResumeLocation = ReaderTextStartLocation(request.chapterIndex, request.charOffset)
        activeQueue = emptyList()
        activeQueueIndex = 0
        audioFocusManager.clearAutoResume()
        val library = storyApplication.awaitLibraryReady()
        check(library is LibraryInitializationState.Ready) { "书库尚未就绪，请打开应用重试" }
        if (!isCurrent(token)) return
        activeChapters = repository.getChapters(request.bookId).sortedBy(Chapter::chapterIndex)
        check(activeChapters.isNotEmpty()) { "书籍目录不可用，请返回书架检查书籍" }
        activeSettings = withContext(Dispatchers.IO) { settingsStore.load().ttsSettings }
        controller.preparePlayback(request, activeSettings)
        if (!keepTimer) remainingTimerMillis = activeSettings.timerPreset.toInitialTimerMillis()
        controller.updateRemainingTimerMillis(remainingTimerMillis)
        refreshNotification()
        val queue = loadChapterQueue(request.bookId, requireNotNull(pausedResumeLocation))
        if (!isCurrent(token)) return
        if (queue.isEmpty()) {
            completeBook(token)
            return
        }
        activeQueue = queue
        activeQueueIndex = 0
        persistLocation(queue.first(), queue.first().segment.startCharOffset)
        if (!isCurrent(token)) return
        initializeEngine()
        if (!isCurrent(token)) return
        engine?.applySettings(activeSettings)
        check(requestAudioFocus()) { "无法获取音频焦点，请关闭其他播放后重试" }
        speakCurrentSegment(token)
    }

    private suspend fun initializeEngine() {
        val pending = engineInitialization ?: run {
            val created = storyApplication.createReaderTtsEngine(this)
            engine = created
            // Shared initialization survives a user pause. A timed-out caller can still exit promptly.
            serviceScope.async { created.initialize() }.also { engineInitialization = it }
        }
        val voices = pending.await().getOrElse { failure ->
            engine?.shutdown()
            engine = null
            engineInitialization = null
            throw failure
        }
        controller.updateAvailableVoices(voices)
    }

    private suspend fun resumePlayback(fromAudioFocus: Boolean, token: Long) {
        if (!fromAudioFocus) {
            audioFocusManager.clearAutoResume()
            abandonAudioFocus()
        }
        var request = activeStartRequest
        if (request == null) {
            val progress = progressStore.loadLast()
            check(progress != null && !progress.completed) { "没有可继续的听书位置，请从正文开始" }
            request = ReaderTtsStartRequest(
                progress.bookId, progress.bookTitle, progress.chapterIndex, progress.charOffset,
                progress.chapterTitle, "朗读中",
            )
            if (!isCurrent(token)) return
            startPlayback(request, token, keepTimer = false)
            return
        }
        val location = pausedResumeLocation ?: currentRecoverableLocation()
            ?: ReaderTextStartLocation(request.chapterIndex, request.charOffset)
        startPlayback(
            request.copy(chapterIndex = location.chapterIndex, charOffset = location.charOffset),
            token, keepTimer = true,
        )
    }

    private suspend fun loadChapterQueue(bookId: String, location: ReaderTextStartLocation): List<QueuedSegment> {
        for (chapter in activeChapters) {
            coroutineContext.ensureActive()
            if (chapter.chapterIndex < location.chapterIndex) continue
            val text = repository.getChapterText(bookId, chapter.chapterIndex)
                ?: error("无法读取「${chapter.title}」，请返回书架检查书籍")
            val offset = if (chapter.chapterIndex == location.chapterIndex) location.charOffset else 0
            val segments = withContext(Dispatchers.Default) {
                ReaderTtsSegmenter.segment(chapter.chapterIndex, text,
                    maxChunkChars = SPEECH_CHUNK_CHARACTERS, startCharOffset = offset)
            }
            if (segments.isNotEmpty()) return segments.map { QueuedSegment(it, chapter.title) }
        }
        return emptyList()
    }

    private fun prepareUpcomingAudio(token: Long, utteranceId: String) {
        val request = activeStartRequest ?: return
        val chapter = activeQueue.lastOrNull()?.segment?.chapterIndex ?: return
        val upcoming = (1..2).mapNotNull { activeQueue.getOrNull(activeQueueIndex + it)?.segment }
        if (upcoming.isNotEmpty()) engine?.prepareUpcoming(upcoming)
        if (nextChapter == null) nextChapter = serviceScope.async {
            withTimeout(PREPARATION_TIMEOUT_MILLIS) {
                loadChapterQueue(request.bookId, ReaderTextStartLocation(chapter + 1, 0))
            }
        }
        if (upcoming.size == 2) return
        val pending = requireNotNull(nextChapter)
        serviceScope.launch {
            try {
                val queue = pending.await()
                if (isCurrent(token) && activeUtteranceId == utteranceId) {
                    engine?.prepareUpcoming(upcoming + queue.take(2 - upcoming.size).map { it.segment })
                }
            } catch (_: Exception) {
                // Preparation failure is reported if/when the user actually reaches this chapter.
            }
        }
    }

    private fun speakCurrentSegment(token: Long) {
        if (!isCurrent(token)) return
        val queued = activeQueue.getOrNull(activeQueueIndex) ?: error("朗读位置失效，请重试")
        pausedResumeLocation = ReaderTextStartLocation(queued.segment.chapterIndex, queued.segment.startCharOffset)
        activeStartRequest?.let {
            controller.preparePlayback(it.copy(chapterTitleOrSummary = queued.chapterTitle), activeSettings)
        }
        acquireWakeLock()
        val id = "$token-${++utteranceSequence}"
        activeUtteranceId = id
        activeUtteranceStarted = false
        armUtteranceWatchdog(id, PREPARATION_TIMEOUT_MILLIS, "声音生成超时，请重试")
        check(engine?.speak(id, queued.segment) == true) { "声音无法开始播放，请重试" }
        refreshNotification()
    }

    override fun onUtteranceStarted(utteranceId: String) {
        serviceScope.launch {
            if (utteranceId != activeUtteranceId) return@launch
            activeUtteranceStarted = true
            armUtteranceWatchdog(utteranceId, PLAYBACK_TIMEOUT_MILLIS, "声音播放中断，请继续或重新开始")
            if (controller.runtimeState.value.isVoicePreviewing) {
                refreshNotification()
                return@launch
            }
            val queued = activeQueue.getOrNull(activeQueueIndex) ?: return@launch
            controller.onPlaybackStarted()
            controller.updatePlaybackSnapshot(
                ReaderTtsPlaybackSnapshot(
                    currentSegment = queued.segment,
                    lastConfirmedSpokenRange = ReaderTtsCharacterRange(queued.segment.startCharOffset, queued.segment.endCharOffset),
                    nextRecoverableCharOffset = queued.segment.startCharOffset,
                    nextRecoverableRange = ReaderTtsCharacterRange(queued.segment.startCharOffset, queued.segment.endCharOffset),
                ),
            )
            startTimerTickerIfNeeded()
            prepareUpcomingAudio(playbackToken, utteranceId)
            refreshNotification()
        }
    }

    override fun onUtteranceRangeStart(utteranceId: String, start: Int, end: Int) {
        serviceScope.launch {
            if (utteranceId != activeUtteranceId || controller.runtimeState.value.isVoicePreviewing) return@launch
            val segment = activeQueue.getOrNull(activeQueueIndex)?.segment ?: return@launch
            // Range-start is highlighting, never evidence that these characters finished playing.
            controller.updatePlaybackSnapshot(
                controller.playbackSnapshot.copy(lastConfirmedSpokenRange = segment.sourceRangeForSpokenRange(start, end)),
            )
        }
    }

    override fun onUtteranceCompleted(utteranceId: String) {
        serviceScope.launch {
            if (utteranceId != activeUtteranceId) return@launch
            activeUtteranceId = null
            activeUtteranceStarted = false
            utteranceWatchdog?.cancel()
            utteranceWatchdog = null
            utteranceTimeout = null
            if (controller.runtimeState.value.isVoicePreviewing) {
                finishPreview()
                return@launch
            }
            if (!controller.playbackState.isSpeakingSession()) return@launch
            val token = playbackToken
            try {
                val completed = activeQueue.getOrNull(activeQueueIndex) ?: return@launch
                pausedResumeLocation = ReaderTextStartLocation(completed.segment.chapterIndex, completed.segment.endCharOffset)
                controller.updatePlaybackSnapshot(
                    controller.playbackSnapshot.copy(
                        nextRecoverableCharOffset = completed.segment.endCharOffset,
                        nextRecoverableRange = ReaderTtsCharacterRange(completed.segment.endCharOffset, completed.segment.endCharOffset),
                    ),
                )
                withTimeout(PREPARATION_TIMEOUT_MILLIS) {
                    persistLocation(completed, completed.segment.endCharOffset)
                }
                // Notification time must neither count as preparation timeout nor start a new unit.
                awaitRetainedAudioFocus(token)
                activeQueueIndex++
                if (activeQueueIndex >= activeQueue.size) {
                    val request = activeStartRequest ?: return@launch
                    val queue = withTimeout(PREPARATION_TIMEOUT_MILLIS) {
                        nextChapter?.await() ?: loadChapterQueue(
                            request.bookId, ReaderTextStartLocation(completed.segment.chapterIndex + 1, 0),
                        )
                    }
                    awaitRetainedAudioFocus(token)
                    nextChapter = null
                    activeQueue = queue
                    activeQueueIndex = 0
                    if (queue.isEmpty()) {
                        withTimeout(PREPARATION_TIMEOUT_MILLIS) {
                            completeBook(token)
                        }
                        return@launch
                    }
                }
                speakCurrentSegment(token)
            } catch (failure: Exception) {
                if (isCurrent(token) && failure !is CancellationException) failPlayback(failure.message ?: "下一段准备失败，请重试")
                if (isCurrent(token) && failure is kotlinx.coroutines.TimeoutCancellationException) failPlayback("下一段准备超时，请重试")
            }
        }
    }

    override fun onUtteranceError(utteranceId: String, message: String) {
        serviceScope.launch {
            if (utteranceId == activeUtteranceId) failPlayback(message)
        }
    }

    private suspend fun persistLocation(queued: QueuedSegment, offset: Int, completed: Boolean = false) {
        val request = activeStartRequest ?: return
        val token = playbackToken
        val progress = ListeningProgress(
            request.bookId, request.bookTitle, queued.segment.chapterIndex, offset,
            queued.chapterTitle, System.currentTimeMillis(), completed,
        )
        progressStore.save(progress)
        if (isCurrent(token)) controller.updateListeningProgress(progress)
    }

    private suspend fun completeBook(token: Long) {
        val request = activeStartRequest ?: return
        val checkpoint = controller.runtimeState.value.listeningProgress?.takeIf { it.bookId == request.bookId }
            ?: ListeningProgress(request.bookId, request.bookTitle, request.chapterIndex, request.charOffset,
                request.chapterTitleOrSummary, System.currentTimeMillis())
        val completed = checkpoint.copy(completed = true, updatedAt = System.currentTimeMillis())
        progressStore.save(completed)
        if (!isCurrent(token)) return
        controller.updateListeningProgress(completed)
        controller.stopAtBookEnd()
        stopPlaybackService()
    }

    private fun pauseByUserAction() {
        audioFocusManager.clearAutoResume()
        if (controller.runtimeState.value.isVoicePreviewing) {
            invalidatePlayback()
            finishPreview()
            return
        }
        if (!controller.playbackState.isOngoingSession()) return
        pausedResumeLocation = currentRecoverableLocation() ?: pausedResumeLocation
        invalidatePlayback()
        freezeTimerBudget()
        controller.pauseByUser()
        releaseWakeLock()
        abandonAudioFocus()
        refreshNotification()
    }

    private fun pauseForAudioFocusLoss(focusChange: Int) {
        if (controller.runtimeState.value.isVoicePreviewing) {
            pauseByUserAction()
            return
        }
        val temporary = focusChange != AudioManager.AUDIOFOCUS_LOSS
        if (controller.playbackState == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS && !temporary) {
            // A permanent loss can follow a temporary one before it returns focus.
            invalidatePlayback()
            return
        }
        if (!controller.playbackState.isSpeakingSession()) return
        pausedResumeLocation = currentRecoverableLocation() ?: pausedResumeLocation
        // A suspended initial/replacement start owns its own timeout and may still update the
        // controller. There is no current PCM yet; cancel/rebuild that operation on focus return.
        val canRetain = activeUtteranceId != null || preparationJob?.isActive != true
        if (temporary && canRetain && engine?.pauseForAudioFocus() == true) {
            focusPauseGate = CompletableDeferred()
            utteranceTimeout = utteranceTimeout?.let {
                it.copy(remainingMillis = (it.remainingMillis - (SystemClock.elapsedRealtime() - utteranceWatchdogStartedAt)).coerceAtLeast(1))
            }
            utteranceWatchdog?.cancel()
            utteranceWatchdog = null
        } else {
            invalidatePlayback()
        }
        freezeTimerBudget()
        controller.pauseByAudioFocus()
        releaseWakeLock()
        refreshNotification()
    }

    private fun resumeRetainedAudioAfterFocus(): Boolean {
        val gate = focusPauseGate ?: return false
        if (engine?.resumeAfterAudioFocus() != true) return false
        focusPauseGate = null
        controller.resumeFromPause()
        if (!activeUtteranceStarted) {
            activeStartRequest?.let { request ->
                controller.preparePlayback(request.copy(
                    chapterTitleOrSummary = activeQueue.getOrNull(activeQueueIndex)?.chapterTitle ?: request.chapterTitleOrSummary,
                ), activeSettings)
            }
        }
        acquireWakeLock()
        utteranceTimeout?.let { armUtteranceWatchdog(it.id, it.remainingMillis, it.message) }
        if (activeUtteranceStarted) startTimerTickerIfNeeded()
        gate.complete(Unit)
        Log.i(TAG, "focusRestore retained=true utterance=$activeUtteranceId")
        refreshNotification()
        return true
    }

    private suspend fun awaitRetainedAudioFocus(token: Long) {
        while (isCurrent(token)) {
            val gate = focusPauseGate ?: return
            gate.await()
        }
        throw CancellationException("Playback replaced while waiting for audio focus")
    }

    private fun currentRecoverableLocation(): ReaderTextStartLocation? {
        val snapshot = controller.playbackSnapshot
        val segment = snapshot.currentSegment ?: return pausedResumeLocation
        return ReaderTextStartLocation(segment.chapterIndex, snapshot.nextRecoverableCharOffset ?: segment.startCharOffset)
    }

    private suspend fun previewVoice(settings: ReaderTtsSettings, token: Long) {
        controller.setVoicePreviewing(true)
        refreshNotification()
        initializeEngine()
        if (!isCurrent(token)) return
        engine?.applySettings(settings)
        check(requestAudioFocus()) { "无法获取音频焦点，请重试试听" }
        val text = "夜色渐深，他合上书，望向窗外。明天的故事，仍在前方等着我们。"
        val segment = ReaderTtsSegment(0, 0, text.length, text)
        val id = "$token-preview-${++utteranceSequence}"
        activeUtteranceId = id
        armUtteranceWatchdog(id, PREPARATION_TIMEOUT_MILLIS, "试听准备超时，请重试")
        check(engine?.speak(id, segment) == true) { "无法播放试听，请重试" }
    }

    private fun finishPreview(error: String? = null) {
        activeUtteranceId = null
        utteranceWatchdog?.cancel()
        engine?.stop()
        engine?.applySettings(activeSettings)
        controller.setVoicePreviewing(false, error)
        releaseWakeLock()
        abandonAudioFocus()
        if (activeStartRequest == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
        } else refreshNotification()
    }

    private fun applyRuntimeSettings(settings: ReaderTtsSettings) {
        val changedTimer = activeSettings.timerPreset != settings.timerPreset
        val changedSynthesis = activeSettings.voiceName != settings.voiceName ||
            activeSettings.speechRate != settings.speechRate
        activeSettings = settings
        if (!controller.runtimeState.value.isVoicePreviewing) {
            engine?.applySettings(settings)
            if (changedSynthesis) {
                // The current taken/playing sentence keeps its original speed. Refill
                // only the next two units, including the next chapter, at the new speed.
                activeUtteranceId?.let { prepareUpcomingAudio(playbackToken, it) }
            }
        }
        if (changedTimer) resetTimerBudget(settings.timerPreset)
        refreshNotification()
    }

    private fun promoteToForeground() {
        notificationFactory.ensureChannel()
        mediaSession.isActive = true
        updateMediaSession()
        ServiceCompat.startForeground(
            this, ReaderTtsNotificationFactory.NOTIFICATION_ID,
            notificationFactory.build(controller.runtimeState.value, mediaSession.sessionToken),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        foreground = true
    }

    private fun refreshNotification() {
        if (destroyed || !foreground) return
        updateMediaSession()
        getSystemService(android.app.NotificationManager::class.java).notify(
            ReaderTtsNotificationFactory.NOTIFICATION_ID,
            notificationFactory.build(controller.runtimeState.value, mediaSession.sessionToken),
        )
    }

    private fun updateMediaSession() {
        val state = controller.runtimeState.value
        val mediaState = when {
            state.isVoicePreviewing -> PlaybackState.STATE_PLAYING
            state.playbackState == ReaderTtsSessionState.PLAYING -> PlaybackState.STATE_PLAYING
            state.playbackState == ReaderTtsSessionState.STARTING -> PlaybackState.STATE_BUFFERING
            state.playbackState.isPausedSession() -> PlaybackState.STATE_PAUSED
            state.playbackState == ReaderTtsSessionState.FAILED -> PlaybackState.STATE_ERROR
            else -> PlaybackState.STATE_STOPPED
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                // Android 13+ builds media-card buttons from PlaybackState, not notification actions.
                .addCustomAction(ReaderTtsIntentFactory.ACTION_STOP, "停止", android.R.drawable.ic_menu_close_clear_cancel)
                .setState(mediaState, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (mediaState == PlaybackState.STATE_PLAYING) 1f else 0f).build(),
        )
        mediaSession.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, state.currentBookTitle ?: "声音试听")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.currentPlaybackSummary.orEmpty()).build())
        state.currentBookId?.let {
            mediaSession.setSessionActivity(ReaderTtsIntentFactory.createNotificationContentPendingIntent(this, it, state.notificationReaderRequest()))
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (focusRequest != null) return true
        val epoch = ++focusEpoch
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (epoch == focusEpoch) {
                    Log.i(TAG, "audioFocus change=$change state=${controller.playbackState} utterance=$activeUtteranceId elapsedRealtime=${SystemClock.elapsedRealtime()}")
                    if (change == AudioManager.AUDIOFOCUS_LOSS) focusRequest = null
                    audioFocusManager.onFocusChange(change)
                }
            }
            .setWillPauseWhenDucked(true).build()
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) focusRequest = request
        return granted
    }

    private fun abandonAudioFocus() {
        focusEpoch++
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun acquireWakeLock() {
        // Renew at each bounded utterance; all pause/error/stop paths release immediately.
        wakeLock.acquire(PLAYBACK_TIMEOUT_MILLIS + PREPARATION_TIMEOUT_MILLIS)
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun armUtteranceWatchdog(id: String, timeout: Long, message: String) {
        utteranceWatchdog?.cancel()
        utteranceTimeout = UtteranceTimeout(id, timeout, message)
        utteranceWatchdogStartedAt = SystemClock.elapsedRealtime()
        utteranceWatchdog = serviceScope.launch {
            delay(timeout)
            if (id == activeUtteranceId) failPlayback(message)
        }
    }

    private fun startTimerTickerIfNeeded() {
        val budget = remainingTimerMillis ?: return
        if (timerTickerJob != null) return
        countdownBaseRemainingMillis = budget
        countdownStartedAtMillis = SystemClock.elapsedRealtime()
        timerTickerJob = serviceScope.launch {
            while (true) {
                delay(1_000)
                val base = countdownBaseRemainingMillis ?: break
                val startedAt = countdownStartedAtMillis ?: break
                remainingTimerMillis = (base - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0)
                controller.updateRemainingTimerMillis(remainingTimerMillis)
                refreshNotification()
                if (remainingTimerMillis == 0L) {
                    controller.stopByTimer()
                    stopPlaybackService()
                    break
                }
            }
        }
    }

    private fun freezeTimerBudget() {
        val base = countdownBaseRemainingMillis
        val startedAt = countdownStartedAtMillis
        if (base != null && startedAt != null) remainingTimerMillis = (base - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0)
        controller.updateRemainingTimerMillis(remainingTimerMillis)
        timerTickerJob?.cancel()
        timerTickerJob = null
        countdownBaseRemainingMillis = null
        countdownStartedAtMillis = null
    }

    private fun resetTimerBudget(preset: ReaderTtsTimerPreset) {
        freezeTimerBudget()
        remainingTimerMillis = preset.toInitialTimerMillis()
        controller.updateRemainingTimerMillis(remainingTimerMillis)
        if (controller.playbackState == ReaderTtsSessionState.PLAYING) startTimerTickerIfNeeded()
    }

    private fun failPlayback(message: String) {
        if (controller.runtimeState.value.isVoicePreviewing) {
            invalidatePlayback()
            finishPreview(message)
        } else {
            controller.handleStartupFailure(message)
            stopPlaybackService()
        }
    }

    private fun stopPlaybackService() {
        invalidatePlayback()
        freezeTimerBudget()
        remainingTimerMillis = null
        controller.updateRemainingTimerMillis(null)
        controller.setVoicePreviewing(false, controller.localErrorMessage)
        activeQueue = emptyList()
        activeQueueIndex = 0
        activeStartRequest = null
        releaseWakeLock()
        audioFocusManager.clearAutoResume()
        abandonAudioFocus()
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun invalidatePlayback() {
        playbackToken++
        focusPauseGate?.cancel()
        focusPauseGate = null
        preparationJob?.cancel()
        preparationJob = null
        utteranceWatchdog?.cancel()
        utteranceWatchdog = null
        utteranceTimeout = null
        nextChapter?.cancel()
        nextChapter = null
        activeUtteranceId = null
        activeUtteranceStarted = false
        engine?.stop()
    }

    private fun isCurrent(token: Long) = !destroyed && token == playbackToken

    private data class QueuedSegment(val segment: ReaderTtsSegment, val chapterTitle: String)
    private data class UtteranceTimeout(val id: String, val remainingMillis: Long, val message: String)

    companion object {
        private const val TAG = "ReaderTtsService"
        // Short units bound first-sound/resume latency and unfinished-text replay.
        private const val SPEECH_CHUNK_CHARACTERS = 40
        private const val PREPARATION_TIMEOUT_MILLIS = 60_000L
        private const val PLAYBACK_TIMEOUT_MILLIS = 180_000L
    }
}
