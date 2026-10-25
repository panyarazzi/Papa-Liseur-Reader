package com.chmouel.liseur.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReaderSettingsDialog(
    isFullscreen: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
    isTopProgressEnabled: Boolean,
    onTopProgressEnabledChanged: (Boolean) -> Unit,
    topProgressMode: String,
    onTopProgressModeChanged: (String) -> Unit,
    footerProgressMode: String,
    onFooterProgressModeChanged: (String) -> Unit,
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
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ตั้งค่าการอ่าน (Reading Settings)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(scrollState)
            ) {
                // ข้อ 1: Fullscreen Switch
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("โหมดเต็มจอ (Fullscreen)")
                    Switch(
                        checked = isFullscreen,
                        onCheckedChange = onFullscreenChanged
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // ข้อ 2 & 4: Top Progress Toggle & Mode
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("แสดงแถบความคืบหน้าด้านบน (Top Progress)")
                    Switch(
                        checked = isTopProgressEnabled,
                        onCheckedChange = onTopProgressEnabledChanged
                    )
                }
                if (isTopProgressEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("รูปแบบ Top Progress:")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onTopProgressModeChanged("CHAPTER_LEFT_WITH_TITLE") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (topProgressMode == "CHAPTER_LEFT_WITH_TITLE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("หน้าเหลือ + ชื่อบท")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Progress Mode
                Text("รูปแบบ Footer Progress:")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onFooterProgressModeChanged("CHAPTER_LEFT_WITH_TITLE") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (footerProgressMode == "CHAPTER_LEFT_WITH_TITLE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("หน้าเหลือ + ชื่อบท")
                    }
                    Button(
                        onClick = { onFooterProgressModeChanged("OFF") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (footerProgressMode == "OFF") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("ปิด")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // ข้อ 3: Progress Font Size
                Text("ขนาดตัวอักษร Progress: ${progressFontSizeSp.toInt()} sp")
                Slider(
                    value = progressFontSizeSp,
                    onValueChange = onProgressFontSizeChanged,
                    valueRange = 9f..20f
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ข้อ 5: Font Scale (ขยายถึง 3.5f สำหรับ TH Sarabun)
                Text("ขนาดฟอนต์เนื้อหา (Font Scale): ${(currentFontScale * 100).toInt()}%")
                Slider(
                    value = currentFontScale,
                    onValueChange = onFontScaleChanged,
                    valueRange = 0.7f..fontScaleMaxMultiplier
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ข้อ 6: Pinch-to-Zoom ในโหมด E-ink
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ถ่างนิ้วปรับฟอนต์ในโหมด E-ink")
                    Switch(
                        checked = isPinchToZoomEnabledInEink,
                        onCheckedChange = onPinchToZoomChanged
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // ข้อ 7: Margin Top & Bottom
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
            TextButton(onClick = onDismiss) { Text("เสร็จสิ้น") }
        }
    )
}
