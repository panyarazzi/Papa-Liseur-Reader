package com.chmouel.liseur.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReaderSettingsDialog(
    isFullscreen: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
    progressFontSizeSp: Float,
    onProgressFontSizeChanged: (Float) -> Unit,
    currentFontScale: Float,
    onFontScaleChanged: (Float) -> Unit,
    fontScaleMaxMultiplier: Float,
    isPinchToZoomEnabledInEink: Boolean,
    onPinchToZoomChanged: (Boolean) -> Unit,
    marginTopDp: Int,
    onMarginTopChanged: (Int) -> Unit,
    marginBottomDp: Int,
    onMarginBottomChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ตั้งค่าการอ่าน (Reading Settings)") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("โหมดเต็มจอ (Fullscreen)")
                    Switch(
                        checked = isFullscreen,
                        onCheckedChange = onFullscreenChanged
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("ขนาดตัวอักษร Progress: ${progressFontSizeSp.toInt()} sp")
                Slider(
                    value = progressFontSizeSp,
                    onValueChange = onProgressFontSizeChanged,
                    valueRange = 9f..20f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("ขนาดฟอนต์เนื้อหา (Font Scale): ${(currentFontScale * 100).toInt()}%")
                Slider(
                    value = currentFontScale,
                    onValueChange = onFontScaleChanged,
                    valueRange = 0.7f..fontScaleMaxMultiplier
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ถ่างนิ้วปรับฟอนต์ในโหมด E-ink")
                    Switch(
                        checked = isPinchToZoomEnabledInEink,
                        onCheckedChange = onPinchToZoomChanged
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("ระยะขอบบน (Margin Top): ${marginTopDp} dp")
                Slider(
                    value = marginTopDp.toFloat(),
                    onValueChange = { onMarginTopChanged(it.toInt()) },
                    valueRange = 0f..80f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("ระยะขอบล่าง (Margin Bottom): ${marginBottomDp} dp")
                Slider(
                    value = marginBottomDp.toFloat(),
                    onValueChange = { onMarginBottomChanged(it.toInt()) },
                    valueRange = 0f..80f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ตกลง") }
        }
    )
}
