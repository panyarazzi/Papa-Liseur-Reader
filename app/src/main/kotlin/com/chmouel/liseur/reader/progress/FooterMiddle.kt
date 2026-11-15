package com.chmouel.liseur.reader.progress

import com.chmouel.liseur.data.settings.FooterMode

sealed interface FooterMiddle {
    data class TimeInChapter(val minutes: Int) : FooterMiddle
    data class TimeInBook(val minutes: Int) : FooterMiddle
    data class Chapter(val title: String) : FooterMiddle
    data class PagesInChapter(val pages: Int) : FooterMiddle
    data class PagesAndChapter(val page: Int, val totalPages: Int, val title: String) : FooterMiddle
}

/** Chooses the footer's middle slot without estimating unmeasured resources. */
fun footerMiddle(
    progress: ReaderProgress,
    mode: FooterMode,
    reflowable: Boolean = false,
    pageIndex: WholeBookPageIndex? = null,
    currentPage: CurrentResourcePage? = null,
    chapterResourceIndices: List<Int> = emptyList(),
): FooterMiddle? {
    val pageInChapter = if (reflowable && currentPage != null && pageIndex != null) {
        pageIndex.pageInChapter(
            currentPage.resourceIndex,
            currentPage.pageInResource,
            chapterResourceIndices,
        )
    } else null
    val pagesInChapter = if (reflowable && pageIndex != null) {
        pageIndex.pagesInChapter(chapterResourceIndices)
    } else null

    return when (mode) {
        FooterMode.SMART ->
            if (progress.isSpeedMeasured) {
                FooterMiddle.TimeInChapter(progress.minutesLeftInChapter)
            } else {
                progress.chapterTitle?.let(FooterMiddle::Chapter)
            }

        FooterMode.PAGES_LEFT_CHAPTER ->
            chapterPagesLeft(progress, reflowable, pageInChapter, pagesInChapter)
                ?.let(FooterMiddle::PagesInChapter)

        FooterMode.PAGES_LEFT_CHAPTER_TITLE -> {
            val title = progress.chapterTitle?.takeIf { it.isNotBlank() }
            if (title != null && pageInChapter != null && pagesInChapter != null) {
                FooterMiddle.PagesAndChapter(pageInChapter, pagesInChapter, title)
            } else {
                title?.let(FooterMiddle::Chapter)
            }
        }

        FooterMode.TIME_LEFT_BOOK -> FooterMiddle.TimeInBook(progress.minutesLeftInBook)
        FooterMode.CHAPTER_TITLE -> progress.chapterTitle?.let(FooterMiddle::Chapter)
        FooterMode.EMPTY, FooterMode.NONE -> null
    }
}
