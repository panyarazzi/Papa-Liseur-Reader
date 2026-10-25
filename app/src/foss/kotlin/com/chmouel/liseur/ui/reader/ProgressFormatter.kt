package com.chmouel.liseur.ui.reader

object ProgressFormatter {
    fun formatProgress(
        mode: String,
        pagesLeftInChapter: Int,
        chapterTitle: String,
        currentChapterPage: Int,
        totalChapterPages: Int,
        totalProgressPercent: Float
    ): String {
        return when (mode) {
            "OFF" -> ""
            "CHAPTER_LEFT_WITH_TITLE" -> {
                val titlePart = if (chapterTitle.isNotBlank()) chapterTitle else "บทปัจจุบัน"
                "$pagesLeftInChapter หน้าที่เหลือ • $titlePart"
            }
            "PERCENTAGE" -> "${String.format("%.1f", totalProgressPercent)}%"
            "PAGES_CURRENT_TOTAL" -> "$currentChapterPage / $totalChapterPages หน้า"
            else -> "$pagesLeftInChapter หน้าที่เหลือ"
        }
    }
}
