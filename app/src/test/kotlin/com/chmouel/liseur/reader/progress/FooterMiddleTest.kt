package com.chmouel.liseur.reader.progress

import com.chmouel.liseur.data.settings.FooterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FooterMiddleTest {
    private val progress = ReaderProgress(
        position = 42,
        totalPositions = 300,
        totalProgression = 0.14f,
        chapterTitle = "The Sign of Four",
        minutesLeftInChapter = 12,
        minutesLeftInBook = 340,
        positionsLeftInChapter = 17,
        isSpeedMeasured = true,
    )
    private val index = WholeBookPageIndex.empty(3)
        .recording(0, 10)
        .recording(1, 15)
        .recording(2, 12)
    private val current = CurrentResourcePage(2, 4, 12)
    private val chapter = listOf(1, 2)

    @Test
    fun `smart shows chapter time once pace is measured`() {
        assertEquals(FooterMiddle.TimeInChapter(12), footerMiddle(progress, FooterMode.SMART))
    }

    @Test
    fun `smart falls back to chapter title before pace is measured`() {
        assertEquals(
            FooterMiddle.Chapter("The Sign of Four"),
            footerMiddle(progress.copy(isSpeedMeasured = false), FooterMode.SMART),
        )
    }

    @Test
    fun `smart says nothing when chapter title and measured pace are absent`() {
        assertNull(
            footerMiddle(
                progress.copy(isSpeedMeasured = false, chapterTitle = null),
                FooterMode.SMART,
            ),
        )
    }

    @Test
    fun `book time remains available when explicitly requested`() {
        assertEquals(
            FooterMiddle.TimeInBook(340),
            footerMiddle(progress.copy(isSpeedMeasured = false), FooterMode.TIME_LEFT_BOOK),
        )
    }

    @Test
    fun `chapter title mode says nothing for a nameless chapter`() {
        assertNull(footerMiddle(progress.copy(chapterTitle = null), FooterMode.CHAPTER_TITLE))
    }

    @Test
    fun `fixed layout chapter countdown uses its Readium positions`() {
        assertEquals(
            FooterMiddle.PagesInChapter(17),
            footerMiddle(progress, FooterMode.PAGES_LEFT_CHAPTER, reflowable = false),
        )
    }

    @Test
    fun `paginated chapter countdown aggregates exact resource counts`() {
        assertEquals(
            FooterMiddle.PagesInChapter(8),
            footerMiddle(
                progress,
                FooterMode.PAGES_LEFT_CHAPTER,
                reflowable = true,
                pageIndex = index,
                currentPage = current,
                chapterResourceIndices = chapter,
            ),
        )
    }

    @Test
    fun `combined chapter mode displays exact chapter page and title`() {
        assertEquals(
            FooterMiddle.PagesAndChapter(19, 27, "The Sign of Four"),
            footerMiddle(
                progress,
                FooterMode.PAGES_LEFT_CHAPTER_TITLE,
                reflowable = true,
                pageIndex = index,
                currentPage = current,
                chapterResourceIndices = chapter,
            ),
        )
    }

    @Test
    fun `combined chapter mode falls back to title until counts are complete`() {
        assertEquals(
            FooterMiddle.Chapter("The Sign of Four"),
            footerMiddle(
                progress,
                FooterMode.PAGES_LEFT_CHAPTER_TITLE,
                reflowable = true,
                pageIndex = WholeBookPageIndex.empty(3).recording(2, 12),
                currentPage = current,
                chapterResourceIndices = chapter,
            ),
        )
    }

    @Test
    fun `last page of chapter retains a zero countdown`() {
        assertEquals(
            FooterMiddle.PagesInChapter(0),
            footerMiddle(
                progress.copy(positionsLeftInChapter = 0),
                FooterMode.PAGES_LEFT_CHAPTER,
                reflowable = true,
                pageIndex = index,
                currentPage = CurrentResourcePage(2, 12, 12),
                chapterResourceIndices = listOf(2),
            ),
        )
    }

    @Test
    fun `unknown chapter has no page countdown`() {
        assertNull(
            footerMiddle(
                progress.copy(positionsLeftInChapter = null),
                FooterMode.PAGES_LEFT_CHAPTER,
                reflowable = true,
                pageIndex = index,
                currentPage = current,
                chapterResourceIndices = emptyList(),
            ),
        )
    }

    @Test
    fun `empty and hidden modes draw nothing`() {
        assertNull(footerMiddle(progress, FooterMode.EMPTY))
        assertNull(footerMiddle(progress, FooterMode.NONE))
    }
}
