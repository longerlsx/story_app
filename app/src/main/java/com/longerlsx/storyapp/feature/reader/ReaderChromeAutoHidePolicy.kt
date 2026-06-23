package com.longerlsx.storyapp.feature.reader

object ReaderChromeAutoHidePolicy {
    const val AUTO_HIDE_DELAY_MILLIS: Long = 3_000L

    fun remainingDelayMillis(
        chromeMode: ReaderChromeMode,
        lastInteractionAtMs: Long?,
        nowMs: Long,
    ): Long? {
        if (chromeMode != ReaderChromeMode.CHROME_VISIBLE || lastInteractionAtMs == null) {
            return null
        }
        return (AUTO_HIDE_DELAY_MILLIS - (nowMs - lastInteractionAtMs))
            .coerceAtLeast(0L)
    }

    fun shouldHideAfterDelay(
        chromeMode: ReaderChromeMode,
        scheduledInteractionAtMs: Long,
        currentInteractionAtMs: Long?,
    ): Boolean {
        return chromeMode == ReaderChromeMode.CHROME_VISIBLE &&
            currentInteractionAtMs == scheduledInteractionAtMs
    }
}
