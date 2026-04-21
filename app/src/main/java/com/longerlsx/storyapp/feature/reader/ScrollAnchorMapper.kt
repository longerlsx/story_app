package com.longerlsx.storyapp.feature.reader

import kotlin.math.roundToInt

object ScrollAnchorMapper {
    fun toCharOffset(
        contentLength: Int,
        scrollValue: Int,
        maxScrollValue: Int,
    ): Int {
        if (contentLength <= 0 || maxScrollValue <= 0) {
            return 0
        }

        val ratio = scrollValue.toDouble() / maxScrollValue.toDouble()
        return (ratio * contentLength)
            .roundToInt()
            .coerceIn(0, contentLength)
    }

    fun toScrollValue(
        contentLength: Int,
        charOffset: Int,
        maxScrollValue: Int,
    ): Int {
        if (contentLength <= 0 || maxScrollValue <= 0) {
            return 0
        }

        val ratio = charOffset.coerceIn(0, contentLength).toDouble() / contentLength.toDouble()
        return (ratio * maxScrollValue)
            .roundToInt()
            .coerceIn(0, maxScrollValue)
    }
}
