package com.longerlsx.storyapp.feature.reader

import kotlin.math.roundToInt

object ReaderScrollFeedAnchorMapper {
    fun toCharOffset(
        contentLength: Int,
        bodyOffsetPx: Int,
        bodyHeightPx: Int,
        viewportTopPx: Int = 0,
    ): Int {
        if (contentLength <= 0 || bodyHeightPx <= 0) {
            return 0
        }

        val consumedPx = (viewportTopPx - bodyOffsetPx).coerceIn(0, bodyHeightPx)
        val ratio = consumedPx.toFloat() / bodyHeightPx.toFloat()
        return (contentLength * ratio).roundToInt().coerceIn(0, contentLength)
    }

    fun toScrollOffsetPx(
        contentLength: Int,
        charOffset: Int,
        bodyHeightPx: Int,
    ): Int {
        if (contentLength <= 0 || bodyHeightPx <= 0) {
            return 0
        }

        val ratio = charOffset.toFloat().coerceIn(0f, contentLength.toFloat()) / contentLength.toFloat()
        return (bodyHeightPx * ratio).roundToInt().coerceIn(0, bodyHeightPx)
    }

    fun toItemScrollOffsetPx(
        contentLength: Int,
        charOffset: Int,
        bodyOffsetWithinItemPx: Int,
        bodyHeightPx: Int,
        viewportTopPx: Int = 0,
    ): Int {
        return (
            bodyOffsetWithinItemPx +
                toScrollOffsetPx(
                    contentLength = contentLength,
                    charOffset = charOffset,
                    bodyHeightPx = bodyHeightPx,
                ) -
                viewportTopPx
            )
            .coerceAtLeast(0)
    }
}
