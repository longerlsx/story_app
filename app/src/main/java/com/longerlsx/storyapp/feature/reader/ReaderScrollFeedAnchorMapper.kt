package com.longerlsx.storyapp.feature.reader

import kotlin.math.roundToInt

object ReaderScrollFeedAnchorMapper {
    fun toCharOffset(
        contentLength: Int,
        itemOffsetPx: Int,
        itemHeightPx: Int,
    ): Int {
        if (contentLength <= 0 || itemHeightPx <= 0) {
            return 0
        }

        val consumedPx = (-itemOffsetPx).coerceIn(0, itemHeightPx)
        val ratio = consumedPx.toFloat() / itemHeightPx.toFloat()
        return (contentLength * ratio).roundToInt().coerceIn(0, contentLength)
    }

    fun toScrollOffsetPx(
        contentLength: Int,
        charOffset: Int,
        itemHeightPx: Int,
    ): Int {
        if (contentLength <= 0 || itemHeightPx <= 0) {
            return 0
        }

        val ratio = charOffset.toFloat().coerceIn(0f, contentLength.toFloat()) / contentLength.toFloat()
        return (itemHeightPx * ratio).roundToInt().coerceIn(0, itemHeightPx)
    }
}
