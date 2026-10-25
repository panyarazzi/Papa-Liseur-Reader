package com.chmouel.liseur.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmouel.liseur.ui.reader.gestures.pinchToZoomFont

@Composable
fun ReaderScreen(
    isFullscreen: Boolean,
    isTopProgressEnabled: Boolean,
    topProgressMode: String,
    footerProgressMode: String,
    progressFontSizeSp: Float,
    currentFontScale: Float,
    fontScaleMaxMultiplier: Float,
    isPinchToZoomEnabledInEink: Boolean,
    isEinkMode: Boolean,
    marginTopDp: Int,
    marginBottomDp: Int,
    chapterTitle: String,
    pagesLeftInChapter: Int,
    currentChapterPage: Int,
    totalChapterPages: Int,
    totalProgressPercent: Float,
    onFontScaleChanged: (Float) -> Unit
) {
    // ข้อ 7: คำนวณ Margin Top เพิ่มเติมอัตโนมัติหากเปิด Top Progress เพื่อไม่ให้เนื้อหาบรรทัดแรกชนขอบ
    val effectiveTopMargin = if (isTopProgressEnabled) {
        marginTopDp + 22
    } else {
        marginTopDp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pinchToZoomFont(
                enabled = isPinchToZoomEnabledInEink || !isEinkMode,
                currentScale = currentFontScale,
                maxScale = fontScaleMaxMultiplier, // ข้อ 5: ขยายฟอนต์ได้สูงสุด (แก้ปัญหา TH Sarabun Newest เล็กเกินไป)
                onScaleChanged = onFontScaleChanged
            )
    ) {
        // ข้อ 4: แสดง Top Progress (ฝั่งซ้าย: Page left + Chapter title)
        if (isTopProgressEnabled) {
            val topText = ProgressFormatter.formatProgress(
                mode = topProgressMode,
                pagesLeftInChapter = pagesLeftInChapter,
                chapterTitle = chapterTitle,
                currentChapterPage = currentChapterPage,
                totalChapterPages = totalChapterPages,
                totalProgressPercent = totalProgressPercent
            )
            if (topText.isNotBlank()) {
                Text(
                    text = topText,
                    fontSize = progressFontSizeSp.sp, // ข้อ 3: ปรับขนาดตัวอักษร Progress ได้
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                )
            }
        }

        // พื้นที่แสดงเนื้อหานิยาย (พร้อมเว้น Margin Top และ Bottom ตามข้อ 7)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = effectiveTopMargin.dp,
                    bottom = marginBottomDp.dp,
                    start = 16.dp,
                    end = 16.dp
                )
        ) {
            // ส่วนแสดงเนื้อหา EPUB Navigator ภายในแอป
        }

        // ข้อ 2: แสดง Footer Progress (Page left + Chapter title)
        if (footerProgressMode != "OFF") {
            val footerText = ProgressFormatter.formatProgress(
                mode = footerProgressMode,
                pagesLeftInChapter = pagesLeftInChapter,
                chapterTitle = chapterTitle,
                currentChapterPage = currentChapterPage,
                totalChapterPages = totalChapterPages,
                totalProgressPercent = totalProgressPercent
            )
            if (footerText.isNotBlank()) {
                Text(
                    text = footerText,
                    fontSize = progressFontSizeSp.sp, // ข้อ 3
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                )
            }
        }
    }
}
