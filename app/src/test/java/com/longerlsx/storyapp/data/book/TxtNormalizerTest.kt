package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Test

class TxtNormalizerTest {

    @Test
    fun stripsUtf8BomAndNormalizesNewlines() {
        val rawText = "\uFEFF第一行\r\n第二行\r第三行\n"

        val normalized = TxtNormalizer.normalize(rawText)

        assertEquals("第一行\n第二行\n第三行\n", normalized)
    }
}
