package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsSettings

interface ReaderTtsEngine {
    suspend fun initialize(): Result<List<ReaderTtsVoiceOption>>

    fun applySettings(settings: ReaderTtsSettings)

    fun speak(
        utteranceId: String,
        segment: ReaderTtsSegment,
    ): Boolean

    fun stop()

    fun shutdown()

    interface Callback {
        fun onUtteranceStarted(utteranceId: String)

        fun onUtteranceRangeStart(
            utteranceId: String,
            start: Int,
            end: Int,
        )

        fun onUtteranceCompleted(utteranceId: String)

        fun onUtteranceError(
            utteranceId: String,
            message: String,
        )
    }
}
