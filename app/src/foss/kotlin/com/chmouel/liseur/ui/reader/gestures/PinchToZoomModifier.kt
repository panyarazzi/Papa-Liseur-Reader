package com.chmouel.liseur.ui.reader.gestures

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.pinchToZoomFont(
    enabled: Boolean,
    currentScale: Float,
    maxScale: Float,
    onScaleChanged: (Float) -> Unit
): Modifier = if (enabled) {
    this.pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            val newScale = (currentScale * zoom).coerceIn(0.7f, maxScale)
            onScaleChanged(newScale)
        }
    }
} else {
    this
}
