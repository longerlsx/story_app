package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FileHashCalculatorTest {

    @Test
    fun sameFileContentProducesSameHash() {
        val bytes = "同一份正文".encodeToByteArray()

        val first = FileHashCalculator.sha256(bytes)
        val second = FileHashCalculator.sha256(bytes)

        assertEquals(first, second)
    }

    @Test
    fun differentFileContentProducesDifferentHash() {
        val first = FileHashCalculator.sha256("正文A".encodeToByteArray())
        val second = FileHashCalculator.sha256("正文B".encodeToByteArray())

        assertNotEquals(first, second)
    }
}
