package com.longerlsx.storyapp.data.reader

/** Orders newly captured positions even when wall time moves behind a saved checkpoint. */
class ReadingProgressClock(
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) {
    private var latestTimestamp = 0L

    @Synchronized
    fun observe(timestamp: Long) {
        latestTimestamp = maxOf(latestTimestamp, timestamp)
    }

    @Synchronized
    fun nextTimestamp(): Long {
        latestTimestamp = maxOf(wallClockMillis(), latestTimestamp + 1)
        return latestTimestamp
    }
}
