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

    fun remainingSuppressionMillis(
        lastInterruptionAtMs: Long?,
        nowMs: Long,
    ): Long? {
        val interruptionAtMs = lastInterruptionAtMs ?: return null
        return (SUPPRESSION_WINDOW_MS - (nowMs - interruptionAtMs)).coerceAtLeast(0L)
    }

    fun shouldClearExpiredSuppression(
        scheduledInterruptionAtMs: Long,
        currentInterruptionAtMs: Long?,
        nowMs: Long,
    ): Boolean {
        return currentInterruptionAtMs == scheduledInterruptionAtMs &&
            !shouldSuppressFollow(
                lastInterruptionAtMs = scheduledInterruptionAtMs,
                nowMs = nowMs,
            )
    }
}
