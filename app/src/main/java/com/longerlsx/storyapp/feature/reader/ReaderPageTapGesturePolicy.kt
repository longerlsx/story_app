package com.longerlsx.storyapp.feature.reader

object ReaderPageTapGesturePolicy {
    fun hasMovedBeyondTapSlop(
        wasMovedBeyondTapSlop: Boolean,
        distanceFromDownPx: Float,
        touchSlopPx: Float,
    ): Boolean {
        return wasMovedBeyondTapSlop || distanceFromDownPx > touchSlopPx
    }

    fun resolveTapZoneOrNull(
        movedBeyondTapSlop: Boolean,
        upX: Float,
        width: Float,
    ): ReaderTapZone? {
        if (movedBeyondTapSlop) {
            return null
        }
        return ReaderTapZone.resolve(
            x = upX,
            width = width,
        )
    }
}
