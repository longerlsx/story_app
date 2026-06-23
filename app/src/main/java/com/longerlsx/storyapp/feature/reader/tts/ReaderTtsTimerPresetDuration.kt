package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset

fun ReaderTtsTimerPreset.toInitialTimerMillis(): Long? {
    return when (this) {
        ReaderTtsTimerPreset.NoTimer -> null
        is ReaderTtsTimerPreset.Countdown -> minutes * 60_000L
    }
}
