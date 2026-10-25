package com.chmouel.liseur.ui.reader

object ProgressFormatter {
    fun formatProgress(
        mode: String,
        pagesLeftInChapter: Int,
        chapterTitle: String?,
        currentChapterPage: Int,
        totalChapterPages: Int,
        totalProgressPercent: Float
    ): String {
        return when (mode) {
            "OFF" -> ""
            "PERCENTAGE" -> "${(totalProgressPercent * 100).toInt()}%"
            "PAGE_IN_CHAPTER" -> "$currentChapterPage / $totalChapterPages"
            else -> {
                val title = if (!chapterTitle.isNullOrBlank()) chapterTitle else "Current Chapter"
                val pagesText = if (pagesLeftInChapter <= 1) "1 page left" else "$pagesLeftInChapter pages left"
                "$pagesText • $title"
            }
        }
    }
}
