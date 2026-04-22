package com.longerlsx.storyapp.feature.reader.tts

object ReaderTtsFollowSuppressionPolicy {
    private const val SUPPRESSION_WINDOW_MS = 10_000L

    fun shouldSuppressFollow(
        lastInterruptionAtMs: Long?,
        nowMs: Long,
    ): Boolean {
        val interruptionAtMs = lastInterruptionAtMs ?: return false
        return nowMs - interruptionAtMs < SUPPRESSION_WINDOW_MS
    }
}
