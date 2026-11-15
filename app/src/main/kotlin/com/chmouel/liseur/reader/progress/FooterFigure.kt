package com.chmouel.liseur.reader.progress

import com.chmouel.liseur.data.settings.FooterField

sealed interface FooterFigure {
    data class BookPercent(val percent: Int, val left: Boolean) : FooterFigure
    data class ChapterPercent(val percent: Int, val left: Boolean) : FooterFigure
    data class Pages(val page: Int, val pages: Int, val exact: Boolean) : FooterFigure
    data class PagesLeftInBook(val pages: Int, val exact: Boolean) : FooterFigure
    data class PagesInChapter(val page: Int, val pages: Int) : FooterFigure
    data class PagesLeftInChapter(val pages: Int) : FooterFigure
    data class TimeInBook(val minutes: Int) : FooterFigure
    data class TimeInChapter(val minutes: Int) : FooterFigure
    data class Location(val position: Int, val total: Int) : FooterFigure
    data class Clock(val atMillis: Long) : FooterFigure
    data class Battery(val percent: Int, val charging: Boolean) : FooterFigure
    data class TimeAndBattery(val atMillis: Long, val percent: Int, val charging: Boolean) : FooterFigure
}

data class FooterDevice(
    val nowMillis: Long? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
)

/**
 * Chooses a footer figure from Readium page callbacks and exact per-resource
 * counts. A reflowable book has no page total until every reading-order
 * resource has been measured at the current layout.
 */
fun footerFigure(
    field: FooterField,
    progress: ReaderProgress?,
    reflowable: Boolean,
    pageIndex: WholeBookPageIndex?,
    currentPage: CurrentResourcePage?,
    chapterResourceIndices: List<Int>,
    device: FooterDevice = FooterDevice(),
): FooterFigure? {
    if (field == FooterField.EMPTY) return null
    when (field) {
        FooterField.CLOCK -> return device.nowMillis?.let(FooterFigure::Clock)
        FooterField.BATTERY -> return device.batteryPercent
            ?.takeIf { it in 0..100 }
            ?.let { FooterFigure.Battery(it, device.charging) }
        FooterField.TIME_AND_BATTERY -> return if (
            device.nowMillis != null && device.batteryPercent != null &&
            device.batteryPercent in 0..100
        ) {
            FooterFigure.TimeAndBattery(device.nowMillis, device.batteryPercent, device.charging)
        } else null
        else -> Unit
    }

    progress ?: return null
    val bookPages = exactBookPages(reflowable, progress, pageIndex, currentPage)
    val chapterPage = if (reflowable && currentPage != null && pageIndex != null) {
        pageIndex.pageInChapter(
            currentPage.resourceIndex,
            currentPage.pageInResource,
            chapterResourceIndices,
        )
    } else null
    val chapterTotal = if (reflowable && pageIndex != null) {
        pageIndex.pagesInChapter(chapterResourceIndices)
    } else null

    return when (field) {
        FooterField.PERCENT_READ -> FooterFigure.BookPercent(progress.percent, left = false)
        FooterField.PERCENT_LEFT -> FooterFigure.BookPercent(100 - progress.percent, left = true)

        FooterField.PAGE_OF_BOOK -> bookPages?.let {
            FooterFigure.Pages(it.first, it.second, exact = true)
        }

        FooterField.PAGES_LEFT_BOOK -> bookPages?.let {
            FooterFigure.PagesLeftInBook((it.second - it.first).coerceAtLeast(0), exact = true)
        }

        FooterField.PAGES_LEFT_CHAPTER -> exactPagesLeftInChapter(
            progress, reflowable, chapterPage, chapterTotal,
        )?.let(FooterFigure::PagesLeftInChapter)

        FooterField.PAGE_IN_CHAPTER ->
            if (chapterPage != null && chapterTotal != null) {
                FooterFigure.PagesInChapter(chapterPage, chapterTotal)
            } else null

        FooterField.PERCENT_READ_CHAPTER ->
            chapterPercent(progress, reflowable, chapterPage, chapterTotal)
                ?.let { FooterFigure.ChapterPercent(it, left = false) }

        FooterField.PERCENT_LEFT_CHAPTER ->
            chapterPercent(progress, reflowable, chapterPage, chapterTotal)
                ?.let { FooterFigure.ChapterPercent(100 - it, left = true) }

        FooterField.TIME_LEFT_BOOK ->
            progress.takeIf { it.isSpeedMeasured }
                ?.let { FooterFigure.TimeInBook(it.minutesLeftInBook) }

        FooterField.TIME_LEFT_CHAPTER ->
            progress.takeIf { it.isSpeedMeasured && it.positionsLeftInChapter != null }
                ?.let { FooterFigure.TimeInChapter(it.minutesLeftInChapter) }

        FooterField.LOCATION ->
            progress.takeIf { it.totalPositions > 0 }
                ?.let { FooterFigure.Location(it.position, it.totalPositions) }

        FooterField.CLOCK, FooterField.BATTERY, FooterField.TIME_AND_BATTERY, FooterField.EMPTY -> null
    }
}

private fun exactBookPages(
    reflowable: Boolean,
    progress: ReaderProgress,
    pageIndex: WholeBookPageIndex?,
    currentPage: CurrentResourcePage?,
): Pair<Int, Int>? {
    if (!reflowable) {
        if (progress.totalPositions <= 0) return null
        return progress.position to progress.totalPositions
    }
    val index = pageIndex ?: return null
    val current = currentPage ?: return null
    val page = index.pageOfBook(current.resourceIndex, current.pageInResource) ?: return null
    val total = index.totalPages ?: return null
    return page to total
}

private fun exactPagesLeftInChapter(
    progress: ReaderProgress,
    reflowable: Boolean,
    pageInChapter: Int?,
    pagesInChapter: Int?,
): Int? {
    progress.positionsLeftInChapter ?: return null
    if (!reflowable) return progress.positionsLeftInChapter
    val page = pageInChapter ?: return null
    val total = pagesInChapter ?: return null
    return (total - page).coerceAtLeast(0)
}

internal fun chapterPagesLeft(
    progress: ReaderProgress,
    reflowable: Boolean,
    pageInChapter: Int?,
    pagesInChapter: Int?,
): Int? = exactPagesLeftInChapter(
    progress, reflowable, pageInChapter, pagesInChapter,
)

private fun chapterPercent(
    progress: ReaderProgress,
    reflowable: Boolean,
    pageInChapter: Int?,
    pagesInChapter: Int?,
): Int? {
    if (reflowable && pageInChapter != null && pagesInChapter != null && pagesInChapter > 0) {
        return percent(pageInChapter.toDouble() / pagesInChapter)
    }
    val resource = progress.resource ?: return null
    val span = resource.span.takeIf { it > 0.0 } ?: return null
    val positions = progress.totalPositions.takeIf { it > 0 } ?: return null
    return percent((progress.position.toDouble() / positions - resource.start) / span)
}

private fun percent(fraction: Double): Int? {
    if (!fraction.isFinite()) return null
    return (fraction * 100).toInt().coerceIn(0, 100)
}
