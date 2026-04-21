package com.longerlsx.storyapp.feature.reader

enum class ReaderTapZone {
    PREVIOUS,
    TOGGLE_CHROME,
    NEXT;

    companion object {
        fun resolve(
            x: Float,
            width: Float,
        ): ReaderTapZone {
            if (width <= 0f) {
                return TOGGLE_CHROME
            }

            return when {
                x < width * 0.3f -> PREVIOUS
                x > width * 0.7f -> NEXT
                else -> TOGGLE_CHROME
            }
        }
    }
}
