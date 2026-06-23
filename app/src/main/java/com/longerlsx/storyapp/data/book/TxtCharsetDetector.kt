package com.longerlsx.storyapp.data.book

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TxtCharsetDetector {
    private val Gb18030: Charset = Charset.forName("GB18030")

    fun detect(bytes: ByteArray): Charset {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return Charsets.UTF_16LE
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return Charsets.UTF_16BE
        }

        return if (canDecodeStrictly(bytes, Charsets.UTF_8)) {
            Charsets.UTF_8
        } else {
            Gb18030
        }
    }

    private fun canDecodeStrictly(
        bytes: ByteArray,
        charset: Charset,
    ): Boolean {
        val decoder = charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }
}
