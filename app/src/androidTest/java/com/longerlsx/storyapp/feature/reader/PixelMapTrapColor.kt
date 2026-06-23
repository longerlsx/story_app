package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.abs

internal fun PixelMap.containsTrapColor(
    trapColor: Color,
    channelTolerance: Float = 0.05f,
    minimumAlpha: Float = 0.2f,
): Boolean {
    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = this[x, y]
            if (
                pixel.alpha > minimumAlpha &&
                abs(pixel.red - trapColor.red) < channelTolerance &&
                abs(pixel.green - trapColor.green) < channelTolerance &&
                abs(pixel.blue - trapColor.blue) < channelTolerance
            ) {
                return true
            }
        }
    }
    return false
}
