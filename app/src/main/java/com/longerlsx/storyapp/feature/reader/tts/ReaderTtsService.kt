package com.longerlsx.storyapp.feature.reader.tts

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.Chapter
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReaderTtsService : Service(), ReaderTtsEngine.Callback {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val storyApplication by lazy {
        applicationContext as StoryApplication
    }
    private val controller by lazy {
        storyApplication.readerTtsController
    }
    private val repository by lazy {
        storyApplication.bookRepository
    }
    private val settingsStore by lazy {
        storyApplication.readerSettingsStore
    }
    private val notificationFactory by lazy {
        ReaderTtsNotificationFactory(this)
    }
    private val audioManager by lazy {
        getSystemService(AudioManager::class.java)
    }
    private val audioFocusManager by lazy {
        ReaderTtsAudioFocusManager(
            onPauseForFocusLoss = {
                serviceScope.launch {
                    pauseForAudioFocusLoss()
                }
            },
            onResumeAfterFocusGain = {
                serviceScope.launch {
                    resumePlayback(
                        fromAudioFocus = true,
                        token = nextPlaybackToken(),
                    )
                }
            },
        )
    }

    private var engine: ReaderTtsEngine? = null
    private var focusRequest: AudioFocusRequest? = null
    private var activeStartRequest: ReaderTtsStartRequest? = null
    private var activeQueue: List<QueuedSegment> = emptyList()
    private var activeQueueIndex: Int = 0
    private var activeUtteranceId: String? = null
    private var pausedResumeLocation: ReaderTextStartLocation? = null
    private var activeChapters: List<Chapter> = emptyList()
    private var activeSettings: ReaderTtsSettings = ReaderTtsSettings()
    private var remainingTimerMillis: Long? = null
    private var countdownStartedAtMillis: Long? = null
    private var countdownBaseRemainingMillis: Long? = null
    private var timerTickerJob = null as kotlinx.coroutines.Job?
    private var playbackToken: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        serviceScope.launch {
            when (intent?.action) {
                ReaderTtsIntentFactory.ACTION_START -> {
                    val request = ReaderTtsIntentFactory.extractStartRequest(intent)
                    if (request == null) {
                        controller.handleStartupFailure("朗读请求缺少必要参数")
                        stopPlaybackService(clearSnapshot = true)
                    } else {
                        startPlayback(
                            request = request,
                            token = nextPlaybackToken(),
                        )
                    }
                }

                ReaderTtsIntentFactory.ACTION_RESTART -> {
                    val request = ReaderTtsIntentFactory.extractRestartRequest(intent)
                    if (request != null) {
                        restartPlayback(
                            request = request,
                            token = nextPlaybackToken(),
                        )
                    }
                }

                ReaderTtsIntentFactory.ACTION_PAUSE -> pauseByUserAction()
                ReaderTtsIntentFactory.ACTION_RESUME -> resumePlayback(
                    fromAudioFocus = false,
                    token = nextPlaybackToken(),
                )
                ReaderTtsIntentFactory.ACTION_STOP -> {
                    controller.markStoppedByUser()
                    stopPlaybackService(clearSnapshot = true)
                }
                ReaderTtsIntentFactory.ACTION_UPDATE_SETTINGS -> {
                    ReaderTtsIntentFactory.extractSettings(intent)?.let(::applyRuntimeSettings)
                }
                else -> Unit
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        timerTickerJob?.cancel()
        serviceScope.cancel()
        engine?.shutdown()
        engine = null
        abandonAudioFocus()
        super.onDestroy()
    }

    override fun onUtteranceStarted(utteranceId: String) {
        serviceScope.launch {
            if (utteranceId != activeUtteranceId) {
                return@launch
            }
            val queuedSegment = activeQueue.getOrNull(activeQueueIndex) ?: return@launch
            controller.onPlaybackStarted()
            controller.updatePlaybackSummary(queuedSegment.chapterTitle)
            controller.updatePlaybackSnapshot(
                controller.playbackSnapshot.copy(
                    currentSegment = queuedSegment.segment,
                    lastConfirmedSpokenRange = ReaderTtsCharacterRange(
                        queuedSegment.segment.startCharOffset,
                        queuedSegment.segment.endCharOffset,
                    ),
                    nextRecoverableCharOffset = queuedSegment.segment.startCharOffset,
                    nextRecoverableRange = ReaderTtsCharacterRange(
                        queuedSegment.segment.startCharOffset,
                        queuedSegment.segment.endCharOffset,
                    ),
                    activePauseReason = null,
                ),
            )
            startTimerTickerIfNeeded()
            refreshNotification()
        }
    }

    override fun onUtteranceRangeStart(
        utteranceId: String,
        start: Int,
        end: Int,
    ) {
        serviceScope.launch {
            if (utteranceId != activeUtteranceId) {
                return@launch
            }
            val queuedSegment = activeQueue.getOrNull(activeQueueIndex) ?: return@launch
            val absoluteStart = (queuedSegment.segment.startCharOffset + start)
                .coerceAtLeast(queuedSegment.segment.startCharOffset)
            val absoluteEnd = (queuedSegment.segment.startCharOffset + end)
                .coerceIn(absoluteStart, queuedSegment.segment.endCharOffset)
            controller.updatePlaybackSnapshot(
                controller.playbackSnapshot.copy(
                    currentSegment = queuedSegment.segment,
                    lastConfirmedSpokenRange = ReaderTtsCharacterRange(absoluteStart, absoluteEnd),
                    nextRecoverableCharOffset = absoluteEnd,
                    nextRecoverableRange = ReaderTtsCharacterRange(
                        absoluteEnd,
                        queuedSegment.segment.endCharOffset,
                    ),
                    activePauseReason = null,
                ),
            )
        }
    }

    override fun onUtteranceCompleted(utteranceId: String) {
        serviceScope.launch {
            if (utteranceId != activeUtteranceId) {
                return@launch
            }
            if (!controller.playbackState.isSpeakingSession()) {
                return@launch
            }
            val queuedSegment = activeQueue.getOrNull(activeQueueIndex)
            if (queuedSegment != null) {
                controller.updatePlaybackSnapshot(
                    controller.playbackSnapshot.copy(
                        currentSegment = queuedSegment.segment,
                        lastConfirmedSpokenRange = ReaderTtsCharacterRange(
                            queuedSegment.segment.endCharOffset,
                            queuedSegment.segment.endCharOffset,
                        ),
                        nextRecoverableCharOffset = queuedSegment.segment.endCharOffset,
                        nextRecoverableRange = ReaderTtsCharacterRange(
                            queuedSegment.segment.endCharOffset,
                            queuedSegment.segment.endCharOffset,
                        ),
                        activePauseReason = null,
                    ),
                )
            }
            activeQueueIndex += 1
            if (activeQueueIndex >= activeQueue.size) {
                controller.stopAtBookEnd()
                stopPlaybackService(clearSnapshot = true)
            } else {
                speakCurrentSegment()
            }
        }
    }

    override fun onUtteranceError(
        utteranceId: String,
        message: String,
    ) {
        serviceScope.launch {
            if (utteranceId != activeUtteranceId) {
                return@launch
            }
            if (!controller.playbackState.isSpeakingSession()) {
                return@launch
            }
            controller.handleStartupFailure(message)
            stopPlaybackService(clearSnapshot = true)
        }
    }

    private suspend fun startPlayback(
        request: ReaderTtsStartRequest,
        token: Long,
    ) {
        if (!controller.playbackState.isSpeakingSession()) {
            return
        }
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        activeStartRequest = request
        activeQueue = emptyList()
        activeQueueIndex = 0
        pausedResumeLocation = null
        // A service may be the first entry after process death. Promote before waiting for IO.
        promoteToForeground()
        val library = storyApplication.awaitLibraryReady()
        if (!isPlaybackTokenCurrent(token)) return
        if (library !is com.longerlsx.storyapp.LibraryInitializationState.Ready) {
            controller.handleStartupFailure("书库尚未就绪，请打开应用重试。")
            stopPlaybackService(clearSnapshot = true)
            return
        }
        activeChapters = repository.getChapters(request.bookId).sortedBy(Chapter::chapterIndex)
        activeSettings = settingsStore.load().ttsSettings
        remainingTimerMillis = activeSettings.timerPreset.toInitialTimerMillis()
        controller.updateRemainingTimerMillis(remainingTimerMillis)
        refreshNotification()
        val voicesResult = ensureEngineReady()
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        if (voicesResult.isFailure) {
            controller.handleStartupFailure(
                voicesResult.exceptionOrNull()?.message ?: "系统朗读服务初始化失败",
            )
            stopPlaybackService(clearSnapshot = true)
            return
        }
        controller.updateAvailableVoices(voicesResult.getOrNull().orEmpty())
        engine?.applySettings(activeSettings)

        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        if (!requestAudioFocus()) {
            controller.handleStartupFailure("无法获取音频焦点")
            stopPlaybackService(clearSnapshot = true)
            return
        }

        val normalizedStartLocation = normalizeStartLocation(
            bookId = request.bookId,
            requestedLocation = ReaderTextStartLocation(
                chapterIndex = request.chapterIndex,
                charOffset = request.charOffset,
            ),
        )
        if (normalizedStartLocation == null) {
            controller.stopAtBookEnd()
            stopPlaybackService(clearSnapshot = true)
            return
        }
        pausedResumeLocation = normalizedStartLocation
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        activeQueue = buildQueue(
            bookId = request.bookId,
            startLocation = normalizedStartLocation,
        )
        activeQueueIndex = 0
        if (activeQueue.isEmpty()) {
            controller.stopAtBookEnd()
            stopPlaybackService(clearSnapshot = true)
            return
        }
        speakCurrentSegment(token)
    }

    private suspend fun ensureEngineReady(): Result<List<ReaderTtsVoiceOption>> {
        val existing = engine
        if (existing != null) {
            return existing.initialize()
        }
        val newEngine = storyApplication.createReaderTtsEngine(this)
        engine = newEngine
        return newEngine.initialize()
    }

    private suspend fun restartPlayback(
        request: ReaderTtsStartRequest,
        token: Long,
    ) {
        if (!controller.playbackState.isOngoingSession()) {
            return
        }
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        activeStartRequest = request
        audioFocusManager.clearAutoResume()
        val engineReady = ensureEngineReady()
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        if (engineReady.isFailure) {
            controller.handleStartupFailure(
                engineReady.exceptionOrNull()?.message ?: "系统朗读服务初始化失败",
            )
            stopPlaybackService(clearSnapshot = true)
            return
        }
        controller.updateAvailableVoices(engineReady.getOrNull().orEmpty())
        engine?.applySettings(activeSettings)
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        if (focusRequest == null && !requestAudioFocus()) {
            controller.handleStartupFailure("无法获取音频焦点")
            stopPlaybackService(clearSnapshot = true)
            return
        }

        val normalizedStartLocation = normalizeStartLocation(
            bookId = request.bookId,
            requestedLocation = ReaderTextStartLocation(
                chapterIndex = request.chapterIndex,
                charOffset = request.charOffset,
            ),
        )
        if (normalizedStartLocation == null) {
            controller.stopAtBookEnd()
            stopPlaybackService(clearSnapshot = true)
            return
        }

        activeUtteranceId = null
        pausedResumeLocation = normalizedStartLocation
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        activeQueue = buildQueue(
            bookId = request.bookId,
            startLocation = normalizedStartLocation,
        )
        activeQueueIndex = 0
        if (activeQueue.isEmpty()) {
            controller.stopAtBookEnd()
            stopPlaybackService(clearSnapshot = true)
            return
        }
        controller.updatePlaybackSummary(request.chapterTitleOrSummary)
        controller.updatePlaybackSnapshot(ReaderTtsPlaybackSnapshot())
        engine?.stop()
        speakCurrentSegment(token)
    }

    private suspend fun speakCurrentSegment(
        token: Long = playbackToken,
    ) {
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        val queuedSegment = activeQueue.getOrNull(activeQueueIndex)
        if (queuedSegment == null) {
            controller.stopAtBookEnd()
            stopPlaybackService(clearSnapshot = true)
            return
        }
        controller.updatePlaybackSummary(queuedSegment.chapterTitle)
        activeUtteranceId = "${queuedSegment.segment.chapterIndex}-${queuedSegment.segment.startCharOffset}-${SystemClock.elapsedRealtime()}"
        val speakSucceeded = engine?.speak(
            utteranceId = requireNotNull(activeUtteranceId),
            segment = queuedSegment.segment,
        ) == true
        if (!speakSucceeded) {
            controller.handleStartupFailure("系统朗读服务无法开始朗读")
            stopPlaybackService(clearSnapshot = true)
            return
        }
        refreshNotification()
    }

    private fun pauseByUserAction() {
        if (!controller.playbackState.isSpeakingSession()) {
            return
        }
        invalidatePlaybackToken()
        audioFocusManager.clearAutoResume()
        freezeTimerBudget()
        pausedResumeLocation = currentRecoverableLocation() ?: fallbackResumeLocation()
        activeUtteranceId = null
        engine?.stop()
        controller.pauseByUser()
        abandonAudioFocus()
        refreshNotification()
    }

    private fun pauseForAudioFocusLoss() {
        if (!controller.playbackState.isSpeakingSession()) {
            return
        }
        invalidatePlaybackToken()
        freezeTimerBudget()
        pausedResumeLocation = currentRecoverableLocation() ?: fallbackResumeLocation()
        activeUtteranceId = null
        engine?.stop()
        controller.pauseByAudioFocus()
        refreshNotification()
    }

    private fun applyRuntimeSettings(settings: ReaderTtsSettings) {
        val timerPresetChanged = activeSettings.timerPreset != settings.timerPreset
        activeSettings = settings
        engine?.applySettings(settings)
        if (timerPresetChanged) {
            resetTimerBudget(settings.timerPreset)
        } else {
            controller.updateRemainingTimerMillis(remainingTimerMillis)
        }
        refreshNotification()
    }

    private suspend fun resumePlayback(
        fromAudioFocus: Boolean,
        token: Long,
    ) {
        val startRequest = activeStartRequest ?: return
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        val resumeLocation = pausedResumeLocation ?: currentRecoverableLocation() ?: ReaderTextStartLocation(
            chapterIndex = startRequest.chapterIndex,
            charOffset = startRequest.charOffset,
        )
        if (!fromAudioFocus) {
            audioFocusManager.clearAutoResume()
        }
        if (!isPlaybackTokenCurrent(token)) {
            return
        }
        if (!fromAudioFocus && !requestAudioFocus()) {
            controller.handleStartupFailure("无法获取音频焦点")
            stopPlaybackService(clearSnapshot = true)
            return
        }
        activeQueue = buildQueue(
            bookId = startRequest.bookId,
            startLocation = resumeLocation,
        )
        activeQueueIndex = 0
        if (activeQueue.isEmpty()) {
            controller.stopAtBookEnd()
            stopPlaybackService(clearSnapshot = true)
            return
        }
        controller.resumeFromPause()
        speakCurrentSegment(token)
    }

    private suspend fun normalizeStartLocation(
        bookId: String,
        requestedLocation: ReaderTextStartLocation,
    ): ReaderTextStartLocation? {
        val chapters = repository.getChapters(bookId).sortedBy(Chapter::chapterIndex)
        for (chapter in chapters) {
            if (chapter.chapterIndex < requestedLocation.chapterIndex) {
                continue
            }
            val chapterText = repository.getChapterText(bookId, chapter.chapterIndex).orEmpty()
            val chapterStartOffset = if (chapter.chapterIndex == requestedLocation.chapterIndex) {
                requestedLocation.charOffset
            } else {
                0
            }
            val location = ReaderTextStartLocator.resolveRestartLocation(
                chapterIndex = chapter.chapterIndex,
                text = chapterText,
                pressedCharOffset = chapterStartOffset.coerceAtLeast(0),
                nonBodyRanges = emptyList(),
            )
            if (location != null) {
                return location
            }
        }
        return null
    }

    private suspend fun buildQueue(
        bookId: String,
        startLocation: ReaderTextStartLocation,
    ): List<QueuedSegment> {
        val queue = mutableListOf<QueuedSegment>()
        val chapters = repository.getChapters(bookId).sortedBy(Chapter::chapterIndex)
        for (chapter in chapters) {
            if (chapter.chapterIndex < startLocation.chapterIndex) {
                continue
            }
            val chapterText = repository.getChapterText(bookId, chapter.chapterIndex).orEmpty()
            if (chapterText.isBlank()) {
                continue
            }
            val chapterSegments = ReaderTtsSegmenter.segment(
                chapterIndex = chapter.chapterIndex,
                text = chapterText,
            )
            if (chapter.chapterIndex == startLocation.chapterIndex) {
                queue += chapterSegments.mapNotNull { segment ->
                    when {
                        segment.endCharOffset <= startLocation.charOffset -> null
                        startLocation.charOffset <= segment.startCharOffset -> {
                            QueuedSegment(segment = segment, chapterTitle = chapter.title)
                        }

                        else -> {
                            val clippedText = chapterText
                                .substring(
                                    startIndex = startLocation.charOffset.coerceAtMost(chapterText.length),
                                    endIndex = segment.endCharOffset.coerceAtMost(chapterText.length),
                                )
                                .let(ReaderTtsSegmenter::sanitizeForSpeech)
                            if (clippedText.isBlank()) {
                                null
                            } else {
                                QueuedSegment(
                                    segment = ReaderTtsSegment(
                                        chapterIndex = segment.chapterIndex,
                                        startCharOffset = startLocation.charOffset,
                                        endCharOffset = segment.endCharOffset,
                                        spokenText = clippedText,
                                    ),
                                    chapterTitle = chapter.title,
                                )
                            }
                        }
                    }
                }
            } else {
                queue += chapterSegments.map { segment ->
                    QueuedSegment(
                        segment = segment,
                        chapterTitle = chapter.title,
                    )
                }
            }
        }
        return queue
    }

    private fun currentRecoverableLocation(): ReaderTextStartLocation? {
        val currentSegment = controller.playbackSnapshot.currentSegment ?: activeQueue.getOrNull(activeQueueIndex)?.segment
        if (currentSegment == null) {
            return null
        }
        return ReaderTextStartLocation(
            chapterIndex = currentSegment.chapterIndex,
            charOffset = controller.playbackSnapshot.nextRecoverableCharOffset
                ?: currentSegment.startCharOffset,
        )
    }

    private fun fallbackResumeLocation(): ReaderTextStartLocation? {
        val startRequest = activeStartRequest ?: return null
        return ReaderTextStartLocation(
            chapterIndex = startRequest.chapterIndex,
            charOffset = startRequest.charOffset,
        )
    }

    private fun promoteToForeground() {
        notificationFactory.ensureChannel()
        ServiceCompat.startForeground(
            this,
            ReaderTtsNotificationFactory.NOTIFICATION_ID,
            notificationFactory.build(controller.runtimeState.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun refreshNotification() {
        notificationFactory.ensureChannel()
        notificationFactory
        val notification = notificationFactory.build(controller.runtimeState.value)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(ReaderTtsNotificationFactory.NOTIFICATION_ID, notification)
    }

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(audioFocusManager::onFocusChange)
            .setWillPauseWhenDucked(true)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val request = focusRequest ?: return
        audioManager.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    private fun startTimerTickerIfNeeded() {
        val budget = remainingTimerMillis ?: return
        if (timerTickerJob != null) {
            return
        }
        countdownBaseRemainingMillis = budget
        countdownStartedAtMillis = SystemClock.elapsedRealtime()
        timerTickerJob = serviceScope.launch {
            while (true) {
                delay(1_000)
                val base = countdownBaseRemainingMillis ?: break
                val startedAt = countdownStartedAtMillis ?: break
                val updatedRemaining = (base - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)
                remainingTimerMillis = updatedRemaining
                controller.updateRemainingTimerMillis(updatedRemaining)
                refreshNotification()
                if (updatedRemaining <= 0L) {
                    controller.stopByTimer()
                    stopPlaybackService(clearSnapshot = true)
                    break
                }
            }
        }
    }

    private fun freezeTimerBudget() {
        val base = countdownBaseRemainingMillis ?: remainingTimerMillis ?: return
        val startedAt = countdownStartedAtMillis ?: return
        remainingTimerMillis = (base - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)
        controller.updateRemainingTimerMillis(remainingTimerMillis)
        timerTickerJob?.cancel()
        timerTickerJob = null
        countdownBaseRemainingMillis = null
        countdownStartedAtMillis = null
    }

    private fun resetTimerBudget(timerPreset: ReaderTtsTimerPreset) {
        timerTickerJob?.cancel()
        timerTickerJob = null
        countdownBaseRemainingMillis = null
        countdownStartedAtMillis = null
        remainingTimerMillis = timerPreset.toInitialTimerMillis()
        controller.updateRemainingTimerMillis(remainingTimerMillis)
        if (remainingTimerMillis != null && controller.playbackState == ReaderTtsSessionState.PLAYING) {
            startTimerTickerIfNeeded()
        }
    }

    private fun stopPlaybackService(clearSnapshot: Boolean = false) {
        invalidatePlaybackToken()
        timerTickerJob?.cancel()
        timerTickerJob = null
        countdownStartedAtMillis = null
        countdownBaseRemainingMillis = null
        activeUtteranceId = null
        engine?.stop()
        activeQueue = emptyList()
        activeQueueIndex = 0
        activeUtteranceId = null
        activeStartRequest = null
        pausedResumeLocation = null
        remainingTimerMillis = null
        if (clearSnapshot) {
            controller.updateRemainingTimerMillis(null)
            controller.updatePlaybackSnapshot(ReaderTtsPlaybackSnapshot())
        }
        audioFocusManager.clearAutoResume()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun nextPlaybackToken(): Long {
        playbackToken += 1
        return playbackToken
    }

    private fun invalidatePlaybackToken() {
        playbackToken += 1
    }

    private fun isPlaybackTokenCurrent(token: Long): Boolean = playbackToken == token

    private data class QueuedSegment(
        val segment: ReaderTtsSegment,
        val chapterTitle: String,
    )
}
