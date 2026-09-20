package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sign

/** The raw gesture supplies intent even when Pager has no prepared boundary preview. */
internal fun readerPageTurnForRelease(distanceInPages: Float, velocityDpPerSecond: Float): Int =
    when {
        abs(velocityDpPerSecond) < 400f -> if (abs(distanceInPages) > 0.5f) distanceInPages.sign.toInt() else 0
        velocityDpPerSecond > 0f -> ceil(distanceInPages).toInt().coerceIn(-1, 1)
        else -> floor(distanceInPages).toInt().coerceIn(-1, 1)
    }

/** Observe in Initial pass so releasing input precedes Pager's fling; never consume movement. */
internal fun Modifier.readerPageGestureInput(
    enabled: Boolean,
    longPressEnabled: Boolean,
    forwardSign: Float,
    density: Float,
    onStart: () -> Long?,
    onDrag: (Long) -> Unit,
    onRelease: (Long, Int) -> Unit,
    onCancel: (Long) -> Unit,
): Modifier = composed {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    pointerInput(enabled, longPressEnabled, forwardSign, density) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val owner = start() ?: return@awaitEachGesture
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var horizontalDrag = false
            var rejected = false
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.position - down.position
                    val elapsed = change.uptimeMillis - down.uptimeMillis
                    if (event.changes.any { it.id != down.id && it.pressed } ||
                        (!horizontalDrag && longPressEnabled && elapsed >= viewConfiguration.longPressTimeoutMillis)
                    ) rejected = true
                    if (!horizontalDrag && delta.getDistance() > viewConfiguration.touchSlop) {
                        if (abs(delta.x) > abs(delta.y) && !rejected) {
                            horizontalDrag = true
                            drag(owner)
                        } else rejected = true
                    }
                    velocity.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (!rejected && horizontalDrag && change.previousPressed) {
                            release(owner, readerPageTurnForRelease(
                                distanceInPages = delta.x * forwardSign / size.width.coerceAtLeast(1),
                                velocityDpPerSecond = velocity.calculateVelocity().x * forwardSign / density,
                            ))
                        } else cancel(owner)
                        released = true
                        break
                    }
                }
            } finally {
                if (!released) cancel(owner)
            }
        }
    }
}
