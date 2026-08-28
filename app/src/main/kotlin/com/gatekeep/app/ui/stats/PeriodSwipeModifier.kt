package com.gatekeep.app.ui.stats

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

fun Modifier.periodSwipe(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean = true,
    thresholdPx: Float = 96f,
): Modifier = pointerInput(onPrevious, onNext, canGoNext) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragEnd = {
            when {
                totalDrag <= -thresholdPx && canGoNext -> onNext()
                totalDrag >= thresholdPx -> onPrevious()
            }
            totalDrag = 0f
        },
        onHorizontalDrag = { _, dragAmount ->
            totalDrag += dragAmount
        },
    )
}
