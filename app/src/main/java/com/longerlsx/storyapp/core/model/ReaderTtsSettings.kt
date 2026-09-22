package com.longerlsx.storyapp.core.model

data class ReaderTtsSettings(
    val voiceName: String? = DEFAULT_VOICE_NAME,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val timerPreset: ReaderTtsTimerPreset = ReaderTtsTimerPreset.NoTimer,
) {
    companion object {
        const val DEFAULT_VOICE_NAME = "zipvoice:leijun"
    }
}

sealed interface ReaderTtsTimerPreset {
    val storedValue: String

    data object NoTimer : ReaderTtsTimerPreset {
        override val storedValue: String = "NO_TIMER"
    }

    data class Countdown(
        val minutes: Int,
    ) : ReaderTtsTimerPreset {
        override val storedValue: String = "COUNTDOWN_$minutes"
    }

    companion object {
        private val supportedCountdownMinutes = setOf(15, 30, 60, 90, 120)

        fun fromStoredValue(value: String?): ReaderTtsTimerPreset? {
            return when {
                value == null -> null
                value == NoTimer.storedValue -> NoTimer
                value.startsWith("COUNTDOWN_") -> value
                    .substringAfter("COUNTDOWN_")
                    .toIntOrNull()
                    ?.takeIf(supportedCountdownMinutes::contains)
                    ?.let(::Countdown)
                else -> null
            }
        }
    }
}
