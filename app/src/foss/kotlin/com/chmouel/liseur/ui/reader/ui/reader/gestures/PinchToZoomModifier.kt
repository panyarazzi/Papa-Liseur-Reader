package com.chmouel.liseur.ui.reader.gestures

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.pinchToZoomFont(
    enabled: Boolean,
    currentScale: Float,
    maxScale: Float,
    minScale: Float = 0.6f,
    onScaleChanged: (Float) -> Unit
): Modifier = if (!enabled) this else this.pointerInput(Unit) {
    var accumulatedZoom = currentScale
    detectTransformGestures { _, _, zoom, _ ->
        accumulatedZoom = (accumulatedZoom * zoom).coerceIn(minScale, maxScale)
        onScaleChanged(accumulatedZoom)
    }
}
