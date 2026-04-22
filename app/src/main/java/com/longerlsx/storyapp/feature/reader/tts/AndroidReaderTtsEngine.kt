package com.longerlsx.storyapp.feature.reader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidReaderTtsEngine(
    private val context: Context,
    private val callback: ReaderTtsEngine.Callback,
) : ReaderTtsEngine {
    private var textToSpeech: TextToSpeech? = null
    private var defaultVoiceName: String? = null

    override suspend fun initialize(): Result<List<ReaderTtsVoiceOption>> {
        textToSpeech?.let { existing ->
            return Result.success(existing.availableVoiceOptions())
        }

        return suspendCancellableCoroutine { continuation ->
            var engineRef: TextToSpeech? = null
            engineRef = TextToSpeech(context.applicationContext) { status ->
                val engine = engineRef
                if (engine == null) {
                    continuation.resume(Result.failure(IllegalStateException("系统朗读服务初始化失败")))
                    return@TextToSpeech
                }
                if (status != TextToSpeech.SUCCESS) {
                    continuation.resume(Result.failure(IllegalStateException("系统朗读服务初始化失败")))
                    return@TextToSpeech
                }
                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            callback.onUtteranceStarted(utteranceId)
                        }

                        override fun onDone(utteranceId: String) {
                            callback.onUtteranceCompleted(utteranceId)
                        }

                        override fun onError(utteranceId: String) {
                            callback.onUtteranceError(utteranceId, "系统朗读出错")
                        }

                        override fun onError(
                            utteranceId: String,
                            errorCode: Int,
                        ) {
                            callback.onUtteranceError(utteranceId, "系统朗读出错：$errorCode")
                        }

                        override fun onRangeStart(
                            utteranceId: String,
                            start: Int,
                            end: Int,
                            frame: Int,
                        ) {
                            callback.onUtteranceRangeStart(utteranceId, start, end)
                        }
                    },
                )
                textToSpeech = engine
                defaultVoiceName = engine.voice?.name
                continuation.resume(Result.success(engine.availableVoiceOptions()))
            }

            continuation.invokeOnCancellation {
                engineRef?.shutdown()
                if (textToSpeech === engineRef) {
                    textToSpeech = null
                }
            }
        }
    }

    override fun applySettings(settings: ReaderTtsSettings) {
        val engine = textToSpeech ?: return
        engine.setSpeechRate(settings.speechRate)
        engine.setPitch(settings.pitch)
        val selectedVoiceName = engine.resolveVoiceCatalog(
            persistedVoiceName = settings.voiceName,
        ).resolvedVoiceName ?: defaultVoiceName
        val selectedVoice = engine.voices
            ?.firstOrNull { it.name == selectedVoiceName }
        if (selectedVoice != null) {
            engine.voice = selectedVoice
        }
    }

    override fun speak(
        utteranceId: String,
        segment: ReaderTtsSegment,
    ): Boolean {
        val engine = textToSpeech ?: return false
        val result = engine.speak(
            segment.spokenText,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId,
        )
        return result == TextToSpeech.SUCCESS
    }

    override fun stop() {
        textToSpeech?.stop()
    }

    override fun shutdown() {
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun TextToSpeech.availableVoiceOptions(): List<ReaderTtsVoiceOption> {
        return resolveVoiceCatalog(persistedVoiceName = null).visibleOptions
    }

    private fun TextToSpeech.resolveVoiceCatalog(
        persistedVoiceName: String?,
    ): ReaderTtsVoiceResolution {
        return ReaderTtsVoiceCatalog.resolve(
            availableVoices = voices
                ?.map { voice ->
                    ReaderTtsRawVoice(
                        name = voice.name,
                        languageTag = voice.locale?.toLanguageTag(),
                        displayName = voice.locale?.displayName,
                    )
                }
                .orEmpty(),
            persistedVoiceName = persistedVoiceName,
        )
    }
}
