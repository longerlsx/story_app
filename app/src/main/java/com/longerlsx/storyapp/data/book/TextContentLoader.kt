package com.longerlsx.storyapp.data.book

import java.io.File

data class LoadedTextContent(
    val charsetName: String,
    val normalizedText: String,
)

class TextContentLoader {
    fun loadNormalizedText(file: File): LoadedTextContent {
        val bytes = file.readBytes()
        val charset = TxtCharsetDetector.detect(bytes)
        val rawText = bytes.toString(charset)
        return LoadedTextContent(
            charsetName = charset.name(),
            normalizedText = TxtNormalizer.normalize(rawText),
        )
    }
}
