package com.chmouel.liseur.reader.progress

import com.chmouel.liseur.data.settings.FooterField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FooterFigureTest {
    private val progress = ReaderProgress(
        position = 42,
        totalPositions = 300,
        totalProgression = 0.14f,
        chapterTitle = "The Sign of Four",
        minutesLeftInChapter = 12,
        minutesLeftInBook = 340,
        positionsLeftInChapter = 17,
        isSpeedMeasured = true,
        resource = BookResource(index = 2, href = "ch3.xhtml", start = 0.10, end = 0.18),
    )
    private val index = WholeBookPageIndex.empty(3)
        .recording(0, 10)
        .recording(1, 15)
        .recording(2, 12)
    private val current = CurrentResourcePage(2, 4, 12)
    private val chapterResources = listOf(1, 2)

    private fun figure(
        field: FooterField,
        progress: ReaderProgress? = this.progress,
        reflowable: Boolean = true,
        pageIndex: WholeBookPageIndex? = index,
        currentPage: CurrentResourcePage? = current,
        chapter: List<Int> = chapterResources,
        device: FooterDevice = FooterDevice(),
    ) = footerFigure(field, progress, reflowable, pageIndex, currentPage, chapter, device)

    @Test
    fun `whole-book page uses the exact prefix and complete total`() {
        assertEquals(FooterFigure.Pages(29, 37, exact = true), figure(FooterField.PAGE_OF_BOOK))
    }

    @Test
    fun `pages left in book uses the exact total`() {
        assertEquals(FooterFigure.PagesLeftInBook(8, exact = true), figure(FooterField.PAGES_LEFT_BOOK))
    }

    @Test
    fun `whole-book total stays hidden until every resource is measured`() {
        val partial = WholeBookPageIndex.empty(3).recording(2, 12)
        assertNull(figure(FooterField.PAGE_OF_BOOK, pageIndex = partial))
        assertNull(figure(FooterField.PAGES_LEFT_BOOK, pageIndex = partial))
    }

    @Test
    fun `chapter page aggregates preceding resources in the chapter`() {
        assertEquals(
            FooterFigure.PagesInChapter(19, 27),
            figure(FooterField.PAGE_IN_CHAPTER),
        )
        assertEquals(
            FooterFigure.PagesLeftInChapter(8),
            figure(FooterField.PAGES_LEFT_CHAPTER),
        )
    }

    @Test
    fun `chapter page remains unavailable while an earlier chapter resource is unmeasured`() {
        val partial = WholeBookPageIndex.empty(3).recording(2, 12)
        assertNull(figure(FooterField.PAGE_IN_CHAPTER, pageIndex = partial))
        assertNull(figure(FooterField.PAGES_LEFT_CHAPTER, pageIndex = partial))
    }

    @Test
    fun `fixed layout keeps Readium positions as exact pages`() {
        assertEquals(
            FooterFigure.Pages(42, 300, exact = true),
            figure(FooterField.PAGE_OF_BOOK, reflowable = false, pageIndex = null, currentPage = null),
        )
    }

    @Test
    fun `book percentages remain based on stable Readium progression`() {
        assertEquals(FooterFigure.BookPercent(14, left = false), figure(FooterField.PERCENT_READ))
        assertEquals(FooterFigure.BookPercent(86, left = true), figure(FooterField.PERCENT_LEFT))
    }

    @Test
    fun `chapter percentages use exact page counts when available`() {
        assertEquals(
            FooterFigure.ChapterPercent(70, left = false),
            figure(FooterField.PERCENT_READ_CHAPTER),
        )
        assertEquals(
            FooterFigure.ChapterPercent(30, left = true),
            figure(FooterField.PERCENT_LEFT_CHAPTER),
        )
    }

    @Test
    fun `chapter percentage can fall back to stable resource progression`() {
        assertEquals(
            FooterFigure.ChapterPercent(50, left = false),
            figure(
                FooterField.PERCENT_READ_CHAPTER,
                reflowable = false,
                pageIndex = null,
                currentPage = null,
            ),
        )
    }

    @Test
    fun `location remains the stable Readium position`() {
        assertEquals(FooterFigure.Location(42, 300), figure(FooterField.LOCATION))
        assertNull(figure(FooterField.LOCATION, progress = progress.copy(totalPositions = 0)))
    }

    @Test
    fun `time fields wait for a measured pace`() {
        val guessing = progress.copy(isSpeedMeasured = false)
        assertNull(figure(FooterField.TIME_LEFT_BOOK, progress = guessing))
        assertNull(figure(FooterField.TIME_LEFT_CHAPTER, progress = guessing))
        assertEquals(FooterFigure.TimeInBook(340), figure(FooterField.TIME_LEFT_BOOK))
        assertEquals(FooterFigure.TimeInChapter(12), figure(FooterField.TIME_LEFT_CHAPTER))
    }

    @Test
    fun `an unresolved chapter has no chapter countdown`() {
        assertNull(
            figure(
                FooterField.PAGES_LEFT_CHAPTER,
                progress = progress.copy(positionsLeftInChapter = null),
                chapter = emptyList(),
            ),
        )
    }

    @Test
    fun `clock and battery do not depend on a book position`() {
        val device = FooterDevice(nowMillis = 1_700_000_000_000L, batteryPercent = 78)
        assertEquals(
            FooterFigure.Clock(1_700_000_000_000L),
            figure(FooterField.CLOCK, progress = null, device = device),
        )
        assertEquals(
            FooterFigure.Battery(78, charging = false),
            figure(FooterField.BATTERY, progress = null, device = device),
        )
        assertNull(figure(FooterField.BATTERY, device = FooterDevice(batteryPercent = 120)))
    }

    @Test
    fun `empty edge and missing progress draw nothing`() {
        assertNull(figure(FooterField.EMPTY))
        assertNull(figure(FooterField.PERCENT_READ, progress = null))
    }
}
