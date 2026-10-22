package com.longerlsx.storyapp.feature.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.os.Process
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.longerlsx.storyapp.core.model.ReaderBundledVoices
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Local synthesis and actual audio completion are separate boundaries. No system TTS is used. */
class OfflineReaderTtsEngine(
    context: Context,
    private val callback: ReaderTtsEngine.Callback,
) : ReaderTtsEngine {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val epoch = AtomicLong()
    private val audioLock = Any()
    @Volatile private var closed = false
    @Volatile private var model: OfflineTts? = null
    private var frontend: ReaderTtsChineseFrontend? = null
    @Volatile private var settings = ReaderTtsSettings()
    private var playbackJob: Job? = null
    private var activeTrack: AudioTrack? = null
    private var activeTrackUtterance: String? = null
    private var focusPauseGate: CompletableDeferred<Unit>? = null
    private var focusPauseStartedAt: Long? = null
    private var totalFocusPauseMillis = 0L
    private var resumeCheckpoint: ResumeCheckpoint? = null
    private val preparation = ReaderTtsAudioPreparation(scope, ::generateAudio)

    override suspend fun initialize(): Result<List<ReaderTtsVoiceOption>> {
        return try {
            withContext(Dispatchers.IO) {
                // Keep initialization, generation and release serialized across engine lifetimes.
                nativeMutex.withLock {
                    check(!closed) { "朗读已停止，请重新开始" }
                    if (model == null) {
                        val startedAt = SystemClock.elapsedRealtime()
                        val supported = context.assets.open("$MODEL_DIR/supported-codepoints.txt")
                            .bufferedReader().useLines { lines -> lines.map { it.toInt(16) }.toSet() }
                        check(supported.isNotEmpty()) { "内置中文词典无法读取" }
                        currentCoroutineContext().ensureActive()
                        val created = OfflineTts(
                            assetManager = context.assets,
                            config = OfflineTtsConfig(
                                model = OfflineTtsModelConfig(
                                    vits = OfflineTtsVitsModelConfig(
                                        model = "$MODEL_DIR/model.onnx",
                                        tokens = "$MODEL_DIR/tokens.txt",
                                        lexicon = "$MODEL_DIR/lexicon.txt",
                                        noiseScale = 0.333f,
                                        noiseScaleW = 0.333f,
                                        lengthScale = 1f,
                                    ),
                                    numThreads = 2,
                                ),
                            ),
                        )
                        if (closed) {
                            created.release()
                            throw CancellationException("Engine closed during initialization")
                        }
                        frontend = ReaderTtsChineseFrontend(supported)
                        model = created
                        // The old APK can reconstruct this private dependency from its own assets.
                        // Do not touch books, settings, progress or any other version directories.
                        File(context.filesDir, "offline-voice/zipvoice-distill-int8-v1").let { old ->
                            if (old.exists() && !old.deleteRecursively()) Log.w(TAG, "oldVoiceDataCleanupIncomplete")
                        }
                        Log.i(TAG, "model=piper-aishell3 threads=2 voices=30,151 modelReadyMs=${SystemClock.elapsedRealtime() - startedAt}")
                    }
                }
            }
            Result.success(voices)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(IllegalStateException("离线声音准备失败，请重试：${failure.message}", failure))
        }
    }

    override fun applySettings(settings: ReaderTtsSettings) {
        // Speed and pitch are applied at playback; prepared natural-rate PCM remains valid.
        this.settings = settings.copy(voiceName = ReaderBundledVoices.resolve(settings.voiceName).name)
    }

    override fun prepareNext(segment: ReaderTtsSegment) {
        prepareUpcoming(listOf(segment))
    }

    override fun prepareUpcoming(segments: List<ReaderTtsSegment>) {
        if (!closed && model != null) preparation.prepareUpcoming(segments, settings.copy(speechRate = 1f))
    }

    override fun speak(utteranceId: String, segment: ReaderTtsSegment): Boolean =
        startSpeech(utteranceId, segment, initialNextSegment = null)

    override fun speak(
        utteranceId: String,
        segment: ReaderTtsSegment,
        initialNextSegment: suspend () -> ReaderTtsSegment?,
    ): Boolean = startSpeech(utteranceId, segment, initialNextSegment)

    private fun startSpeech(
        utteranceId: String,
        segment: ReaderTtsSegment,
        initialNextSegment: (suspend () -> ReaderTtsSegment?)?,
    ): Boolean {
        if (closed || model == null || segment.spokenText.isBlank()) return false
        stopCurrentPlayback()
        val generation = epoch.get()
        val selected = settings
        playbackJob = scope.launch {
            try {
                val generated = preparation.takeForPlayback(
                    segment, selected.copy(speechRate = 1f), selected.speechRate, initialNextSegment,
                )
                ensureCurrent(generation)
                play(generation, utteranceId, segment, generated.samples, generated.sampleRate, selected.speechRate, selected.pitch)
                notifyWhilePlaying(generation) { callback.onUtteranceCompleted(utteranceId) }
            } catch (_: CancellationException) {
                // Stopping an old request must neither confirm nor fail a later request.
            } catch (failure: Exception) {
                notifyCurrent(generation) {
                    callback.onUtteranceError(utteranceId, failure.message ?: "离线播放失败，请重试")
                }
            }
        }
        return true
    }

    private suspend fun generateAudio(segment: ReaderTtsSegment, selected: ReaderTtsSettings): ReaderSpeechAudio =
        nativeMutex.withLock {
            val jobContext = currentCoroutineContext()
            jobContext.ensureActive()
            if (closed) throw CancellationException("Engine closed")
            val readyModel = checkNotNull(model)
            val startedAt = SystemClock.elapsedRealtime()
            val cpuStartedAt = Process.getElapsedCpuTime()
            val voice = ReaderBundledVoices.resolve(selected.voiceName)
            Log.i(TAG, "synthesisStart chapter=${segment.chapterIndex} offset=${segment.startCharOffset} end=${segment.endCharOffset} chars=${segment.spokenText.length} sid=${voice.speakerId} elapsedRealtime=$startedAt")
            val preparedText = checkNotNull(frontend).prepare(segment)
            val audio = readyModel.generateWithConfigAndCallback(
                text = preparedText,
                config = GenerationConfig(
                    sid = voice.speakerId,
                    speed = 1f,
                    silenceScale = 0.2f,
                ),
                // JNI requires invoke(float[]) -> Integer; an indy lambda only exposes invoke(Object).
                callback = object : (FloatArray) -> Int {
                    override fun invoke(samples: FloatArray): Int = if (!closed && jobContext.isActive) 1 else 0
                },
            )
            jobContext.ensureActive()
            if (closed) throw CancellationException("Engine closed")
            check(audio.samples.isNotEmpty()) { "这段文字没有生成声音，请重试或换一处开始" }
            check(audio.sampleRate == 22_050) { "内置声音采样率不匹配，请重新安装" }
            val completedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "synthesisMs=${completedAt - startedAt} audioMs=${audio.samples.size.toLong() * 1000 / audio.sampleRate} chars=${segment.spokenText.length} rate=${selected.speechRate} chapter=${segment.chapterIndex} offset=${segment.startCharOffset} end=${segment.endCharOffset} sid=${voice.speakerId} processCpuMs=${Process.getElapsedCpuTime() - cpuStartedAt} elapsedRealtime=$completedAt")
            ReaderSpeechAudio(audio.samples, audio.sampleRate)
        }

    private suspend fun play(
        generation: Long,
        utteranceId: String,
        segment: ReaderTtsSegment,
        samples: FloatArray,
        sampleRate: Int,
        speechRate: Float,
        pitch: Float,
    ) {
        awaitPlaybackAllowed(generation)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT), sampleRate / 5 * 4))
            .build()
        fun logPlayback(event: String) {
            Log.i(TAG, "$event utterance=$utteranceId chapter=${segment.chapterIndex} offset=${segment.startCharOffset} chars=${segment.spokenText.length} elapsedRealtime=${SystemClock.elapsedRealtime()}")
        }
        try {
            logPlayback("trackBuilt")
            synchronized(audioLock) {
                ensureCurrent(generation)
                check(track.state == AudioTrack.STATE_INITIALIZED) { "无法打开音频输出，请重试" }
                activeTrack = track
                activeTrackUtterance = utteranceId
                track.playbackParams = track.playbackParams
                    .setPitch(pitch.coerceIn(0.8f, 1.2f))
                    .setSpeed(speechRate.coerceIn(0.5f, 2f))
                if (focusPauseGate == null) track.play() else track.pause()
            }
            var offset = 0
            var announced = false
            while (offset < samples.size) {
                currentCoroutineContext().ensureActive()
                awaitPlaybackAllowed(generation)
                val written = track.write(samples, offset, minOf(sampleRate / 20, samples.size - offset), AudioTrack.WRITE_BLOCKING)
                ensureCurrent(generation)
                check(written >= 0) { "音频输出已中断，请继续或重新开始" }
                // A concurrent pause may interrupt a blocking write without accepting more frames.
                if (written == 0) {
                    delay(10)
                    continue
                }
                offset += written
                reportResumedPlayback(track)
                if (!announced && track.playbackHeadPosition > 0) {
                    announced = true
                    logPlayback("playStart")
                    notifyWhilePlaying(generation) {
                        callback.onUtteranceStarted(utteranceId)
                        callback.onUtteranceRangeStart(utteranceId, 0, segment.spokenText.length)
                    }
                }
            }
            var lastHead = -1
            var lastAdvancedAt = playbackClockMillis()
            while (track.playbackHeadPosition < samples.size) {
                awaitPlaybackAllowed(generation)
                val head = track.playbackHeadPosition
                reportResumedPlayback(track)
                if (!announced && head > 0) {
                    announced = true
                    logPlayback("playStart")
                    notifyWhilePlaying(generation) {
                        callback.onUtteranceStarted(utteranceId)
                        callback.onUtteranceRangeStart(utteranceId, 0, segment.spokenText.length)
                    }
                }
                if (head != lastHead) {
                    lastHead = head
                    lastAdvancedAt = playbackClockMillis()
                }
                check(playbackClockMillis() - lastAdvancedAt < 5_000) { "音频播放停滞，请继续或重新开始" }
                delay(10)
            }
            if (!announced) {
                logPlayback("playStart")
                notifyWhilePlaying(generation) {
                    callback.onUtteranceStarted(utteranceId)
                    callback.onUtteranceRangeStart(utteranceId, 0, segment.spokenText.length)
                }
            }
            logPlayback("playEnd")
        } finally {
            synchronized(audioLock) {
                if (activeTrack === track) {
                    activeTrack = null
                    activeTrackUtterance = null
                    resumeCheckpoint = null
                }
                runCatching { track.pause() }
                track.flush()
                track.release()
            }
            logPlayback("trackReleased")
        }
    }

    override fun pauseForAudioFocus(): Boolean = synchronized(audioLock) {
        if (closed) return@synchronized false
        if (focusPauseGate != null) return@synchronized true
        val gate = CompletableDeferred<Unit>()
        focusPauseGate = gate
        focusPauseStartedAt = SystemClock.elapsedRealtime()
        try {
            activeTrack?.let { track ->
                track.pause()
                Log.i(TAG, "focusPaused utterance=$activeTrackUtterance session=${track.audioSessionId} frame=${track.playbackHeadPosition}")
            }
        } catch (_: IllegalStateException) {
            // The service will cancel this task and rebuild if the track can no longer be retained.
            return@synchronized false
        }
        true
    }

    override fun resumeAfterAudioFocus(): Boolean {
        val gate = synchronized(audioLock) {
            if (closed) return false
            val pending = focusPauseGate ?: return false
            try {
                activeTrack?.let { track ->
                    resumeCheckpoint = ResumeCheckpoint(
                        requireNotNull(activeTrackUtterance), track.audioSessionId,
                        track.playbackHeadPosition, SystemClock.elapsedRealtime(),
                    )
                    track.play()
                }
            } catch (_: IllegalStateException) {
                return false
            }
            totalFocusPauseMillis += SystemClock.elapsedRealtime() - requireNotNull(focusPauseStartedAt)
            focusPauseStartedAt = null
            focusPauseGate = null
            pending
        }
        gate.complete(Unit)
        return true
    }

    private suspend fun awaitPlaybackAllowed(generation: Long) {
        while (true) {
            currentCoroutineContext().ensureActive()
            ensureCurrent(generation)
            val gate = synchronized(audioLock) { focusPauseGate } ?: return
            gate.await()
        }
    }

    private fun playbackClockMillis(): Long = synchronized(audioLock) {
        val now = SystemClock.elapsedRealtime()
        now - totalFocusPauseMillis - (focusPauseStartedAt?.let { now - it } ?: 0)
    }

    private fun reportResumedPlayback(track: AudioTrack) = synchronized(audioLock) {
        val checkpoint = resumeCheckpoint ?: return@synchronized
        if (activeTrack !== track || focusPauseGate != null) return@synchronized
        val frame = track.playbackHeadPosition
        if (frame > checkpoint.frame) {
            val now = SystemClock.elapsedRealtime()
            Log.i(TAG, "focusResumed utterance=${checkpoint.utteranceId} session=${checkpoint.sessionId} frame=$frame elapsedRealtime=$now resumeDelayMs=${now - checkpoint.requestedAt}")
            resumeCheckpoint = null
        }
    }

    override fun stop() {
        // Invalidating the consumer first prevents a cancelled prefetch from releasing
        // the startup join barrier while its old playback generation is still current.
        stopCurrentPlayback()
        preparation.clear()
        val gate = synchronized(audioLock) {
            focusPauseGate.also {
                focusPauseGate = null
                focusPauseStartedAt = null
                totalFocusPauseMillis = 0
            }
        }
        gate?.cancel()
    }

    private fun stopCurrentPlayback() {
        epoch.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null
        synchronized(audioLock) {
            activeTrack?.let { track ->
                runCatching { track.pause() }
                runCatching { track.flush() }
            }
            activeTrack = null
            activeTrackUtterance = null
            resumeCheckpoint = null
        }
    }

    override fun shutdown() {
        closed = true
        stop()
        scope.launch {
            nativeMutex.withLock {
                model?.release()
                model = null
                frontend = null
            }
            scope.cancel()
        }
    }

    private fun isCurrent(generation: Long) = !closed && epoch.get() == generation

    private fun ensureCurrent(generation: Long) {
        if (!isCurrent(generation)) throw CancellationException("Superseded speech")
    }

    private suspend fun notifyCurrent(generation: Long, action: () -> Unit) = withContext(Dispatchers.Main) {
        if (isCurrent(generation)) action()
    }

    private suspend fun notifyWhilePlaying(generation: Long, action: () -> Unit) {
        while (true) {
            awaitPlaybackAllowed(generation)
            val delivered = withContext(Dispatchers.Main) {
                if (!isCurrent(generation)) true
                else if (synchronized(audioLock) { focusPauseGate != null }) false
                else {
                    action()
                    true
                }
            }
            if (delivered) return
        }
    }

    private data class ResumeCheckpoint(val utteranceId: String, val sessionId: Int, val frame: Int, val requestedAt: Long)

    companion object {
        private const val TAG = "OfflineReaderTts"
        private const val MODEL_DIR = "piper-zh-cn-aishell3-v1"
        private val nativeMutex = Mutex()
        val voices = ReaderBundledVoices.options.map { ReaderTtsVoiceOption(it.name, it.displayName) }
    }
}
