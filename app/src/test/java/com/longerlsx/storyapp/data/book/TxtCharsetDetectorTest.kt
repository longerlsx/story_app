package com.longerlsx.storyapp.data.book

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtCharsetDetectorTest {

    @Test
    fun detectsGbkCompatibleChineseTextAsGb18030WhenUtf8WouldFail() {
        val bytes = "第1章 开始\n中文正文。".toByteArray(Charset.forName("GBK"))

        val charset = TxtCharsetDetector.detect(bytes)

        assertEquals("GB18030", charset.name())
    }

    @Test
    fun keepsUtf8ChineseTextAsUtf8() {
        val bytes = "第1章 开始\n中文正文。".encodeToByteArray()

        val charset = TxtCharsetDetector.detect(bytes)

        assertEquals("UTF-8", charset.name())
    }
}
