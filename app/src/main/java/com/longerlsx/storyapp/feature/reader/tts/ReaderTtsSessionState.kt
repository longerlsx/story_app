package com.longerlsx.storyapp.feature.reader.tts

enum class ReaderTtsSessionState {
    OFF,
    STARTING,
    PLAYING,
    PAUSED_BY_USER,
    PAUSED_BY_AUDIO_FOCUS,
    STOPPED_BY_USER,
    STOPPED_BY_NAVIGATION,
    STOPPED_BY_TIMER,
    STOPPED_AT_BOOK_END,
    FAILED,
}

fun ReaderTtsSessionState.isOngoingSession(): Boolean {
    return this == ReaderTtsSessionState.STARTING ||
        this == ReaderTtsSessionState.PLAYING ||
        this == ReaderTtsSessionState.PAUSED_BY_USER ||
        this == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS
}
