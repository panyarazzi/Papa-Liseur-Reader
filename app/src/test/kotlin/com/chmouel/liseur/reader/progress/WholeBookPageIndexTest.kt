package com.chmouel.liseur.reader.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeBookPageIndexTest {
    @Test
    fun `whole book total stays unavailable until every resource is measured`() {
        val index = WholeBookPageIndex.empty(3)
            .recording(0, 4)
            .recording(2, 6)

        assertFalse(index.isComplete)
        assertNull(index.totalPages)
    }

    @Test
    fun `page of book uses exact counts of preceding resources`() {
        val index = WholeBookPageIndex.empty(3)
            .recording(0, 4)
            .recording(1, 7)
            .recording(2, 6)

        assertTrue(index.isComplete)
        assertEquals(17, index.totalPages)
        assertEquals(12, index.pageOfBook(resourceIndex = 2, pageInResource = 1))
        assertEquals(17, index.pageOfBook(resourceIndex = 2, pageInResource = 6))
    }

    @Test
    fun `page of book is unavailable when a preceding resource is unmeasured`() {
        val index = WholeBookPageIndex.empty(3)
            .recording(0, 4)
            .recording(2, 6)

        assertNull(index.pageOfBook(resourceIndex = 2, pageInResource = 2))
    }

    @Test
    fun `chapter count sums its resources and never guesses missing counts`() {
        val index = WholeBookPageIndex.empty(4)
            .recording(0, 4)
            .recording(1, 7)
            .recording(2, 6)

        assertNull(index.pagesInChapter(listOf(1, 3)))
        assertNull(index.pageInChapter(2, 3, listOf(1, 2, 3)))

        val complete = index.recording(3, 2)
        assertEquals(8, complete.pagesInChapter(listOf(2, 3)))
        assertEquals(8, complete.pageInChapter(3, 2, listOf(2, 3)))
    }

    @Test
    fun `invalid and out of range page measurements are ignored`() {
        val index = WholeBookPageIndex.empty(2)
            .recording(-1, 10)
            .recording(2, 10)
            .recording(0, 0)

        assertTrue(index.pagesByResource.isEmpty())
        assertNull(index.pageOfBook(0, 1))
    }

    @Test
    fun `a changed resource count replaces only that resource measurement`() {
        val index = WholeBookPageIndex.empty(2)
            .recording(0, 4)
            .recording(1, 7)
            .recording(0, 5)

        assertEquals(5, index.pagesByResource[0])
        assertEquals(7, index.pagesByResource[1])
        assertEquals(12, index.totalPages)
    }
}
