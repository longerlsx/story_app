package com.longerlsx.storyapp.data.book

object TxtNormalizer {
    fun normalize(rawText: String): String {
        return rawText
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }
}
