package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsSettings

interface ReaderTtsEngine {
    suspend fun initialize(): Result<List<ReaderTtsVoiceOption>>

    fun applySettings(settings: ReaderTtsSettings)

    fun speak(
        utteranceId: String,
        segment: ReaderTtsSegment,
    ): Boolean

    /** Keep at most the next short unit ready; it must never start playing on its own. */
    fun prepareNext(segment: ReaderTtsSegment) = Unit

    /** Prepare up to two future units without playing; older engines retain one-unit behavior. */
    fun prepareUpcoming(segments: List<ReaderTtsSegment>) {
        segments.firstOrNull()?.let(::prepareNext)
    }

    /** Retain current audio and bounded preparation while temporary audio focus is absent. */
    fun pauseForAudioFocus(): Boolean = false

    /** Continue the retained audio; false asks the service to rebuild from its saved boundary. */
    fun resumeAfterAudioFocus(): Boolean = false

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
