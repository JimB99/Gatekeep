package com.gatekeep.app.ui.stats

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

fun Modifier.periodSwipe(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean = true,
    thresholdPx: Float = 96f,
): Modifier = pointerInput(onPrevious, onNext, canGoNext, thresholdPx) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var totalDragX = 0f
        var totalDragY = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            totalDragX += delta.x
            totalDragY += delta.y
            if (abs(totalDragX) > abs(totalDragY) && abs(totalDragX) > viewConfiguration.touchSlop) {
                change.consume()
            }
        }
        when {
            totalDragX <= -thresholdPx && canGoNext -> onNext()
            totalDragX >= thresholdPx -> onPrevious()
        }
    }
}
