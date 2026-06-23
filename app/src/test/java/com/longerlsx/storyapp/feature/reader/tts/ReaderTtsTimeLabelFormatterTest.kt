package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTtsTimeLabelFormatterTest {

    @Test
    fun formatRemainingMillisRoundsPositiveValuesUpToTheNextMinute() {
        assertEquals("1m", ReaderTtsTimeLabelFormatter.formatRemainingMillis(1L))
        assertEquals("1m", ReaderTtsTimeLabelFormatter.formatRemainingMillis(59_999L))
        assertEquals("2m", ReaderTtsTimeLabelFormatter.formatRemainingMillis(60_001L))
        assertEquals("1h 30m", ReaderTtsTimeLabelFormatter.formatRemainingMillis(90 * 60_000L))
    }

    @Test
    fun formatPositiveRemainingMillisSuppressesMissingOrExpiredBudgets() {
        assertNull(ReaderTtsTimeLabelFormatter.formatPositiveRemainingMillisOrNull(null))
        assertNull(ReaderTtsTimeLabelFormatter.formatPositiveRemainingMillisOrNull(0L))
        assertNull(ReaderTtsTimeLabelFormatter.formatPositiveRemainingMillisOrNull(-1L))
        assertEquals("1m", ReaderTtsTimeLabelFormatter.formatPositiveRemainingMillisOrNull(1L))
    }
}
