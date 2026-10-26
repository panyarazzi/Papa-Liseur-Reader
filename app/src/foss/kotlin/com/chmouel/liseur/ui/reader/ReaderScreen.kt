package com.chmouel.liseur.ui.reader

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmouel.liseur.data.preferences.ReadingPreferences
import com.chmouel.liseur.ui.reader.gestures.pinchToZoomFont
import enum_types.ProgressDisplayMode

/**
 * ฟังก์ชันควบคุมการซ่อนหรือแสดง Status Bar และ Navigation Bar ตามสวิตช์ Fullscreen
 */
@Composable
private fun ImmersiveMode(hideSystemBars: Boolean) {
    val view = LocalView.current
    LaunchedEffect(hideSystemBars) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideSystemBars) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun ReaderScreen(
    preferences: ReadingPreferences,
    chapterTitle: String,
    pagesLeftInChapter: Int,
    currentChapterPage: Int,
    totalChapterPages: Int,
    totalProgressPercent: Float,
    onFontScaleChanged: (Float) -> Unit,
    onToggleFullscreen: () -> Unit
) {
    // 1. ผูกสถานะ Fullscreen เข้ากับ ImmersiveMode ทันทีที่หน้าจอนี้ทำงาน
    ImmersiveMode(hideSystemBars = preferences.isFullscreen)

    // ข้อ 7: คำนวณ Margin Top เพิ่มเติมอัตโนมัติหากมีการเปิดแสดง Top Progress
    val effectiveTopMargin = if (preferences.isTopProgressEnabled) {
        preferences.marginTopDp + 20 
    } else {
        preferences.marginTopDp
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pinchToZoomFont(
                    enabled = preferences.isPinchToZoomEnabledInEink || !preferences.isEinkMode,
                    currentScale = preferences.currentFontScale,
                    maxScale = preferences.fontScaleMaxMultiplier,
                    onScaleChanged = onFontScaleChanged
                )
        ) {
            // ==================== TOP PROGRESS ====================
            if (preferences.isTopProgressEnabled) {
                val topText = ProgressFormatter.formatProgress(
                    mode = preferences.topProgressMode,
                    pagesLeftInChapter = pagesLeftInChapter,
                    chapterTitle = chapterTitle,
                    currentChapterPage = currentChapterPage,
                    totalChapterPages = totalChapterPages,
                    totalProgressPercent = totalProgressPercent
                )
                
                Text(
                    text = topText,
                    fontSize = preferences.progressFontSizeSp.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                )
            }

            // ==================== READING CONTENT AREA ====================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = effectiveTopMargin.dp,
                        bottom = preferences.marginBottomDp.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
            ) {
                EpubNavigatorView(
                    fontScale = preferences.currentFontScale
                )
            }

            // ==================== FOOTER PROGRESS ====================
            if (preferences.footerProgressMode != "OFF") {
                val footerText = ProgressFormatter.formatProgress(
                    mode = preferences.footerProgressMode,
                    pagesLeftInChapter = pagesLeftInChapter,
                    chapterTitle = chapterTitle,
                    currentChapterPage = currentChapterPage,
                    totalChapterPages = totalChapterPages,
                    totalProgressPercent = totalProgressPercent
                )

                Text(
                    text = footerText,
                    fontSize = preferences.progressFontSizeSp.sp,
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

@Composable
fun EpubNavigatorView(fontScale: Float) {
    // Component สำหรับโหลดเนื้อหา EPUB
}
