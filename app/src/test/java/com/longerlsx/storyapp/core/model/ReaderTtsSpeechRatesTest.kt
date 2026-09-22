package com.longerlsx.storyapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsSpeechRatesTest {
    @Test
    fun visibleLabelsRepresentTheActualSelectableSpeedsWithoutRounding() {
        assertEquals(
            listOf(0.85f to "0.85×", 1f to "1×", 1.3f to "1.3×", 1.45f to "1.45×", 2f to "2×"),
            ReaderTtsSpeechRates.options.map { it to ReaderTtsSpeechRates.label(it) },
        )
    }
}
