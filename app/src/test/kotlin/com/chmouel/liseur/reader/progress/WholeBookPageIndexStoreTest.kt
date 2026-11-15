package com.chmouel.liseur.reader.progress

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WholeBookPageIndexStoreTest {
    @Test
    fun `visible Readium page index is normalized from zero based to one based`() {
        val store = WholeBookPageIndexStore(resourceCount = 2)

        store.onVisiblePageChanged(
            resourceIndex = 1,
            pageIndexZeroBased = 2,
            totalPages = 5,
        )

        assertEquals(CurrentResourcePage(1, 3, 5), store.currentPage.value)
        // The visible callback is not allowed to seed the measured whole-book index.
        assertFalse(store.index.value.isComplete)
        assertEquals(emptyMap<Int, Int>(), store.index.value.pagesByResource)
    }

    @Test
    fun `layout reset clears page facts and advances the measurement generation`() {
        val store = WholeBookPageIndexStore(resourceCount = 2)
        store.recordResource(0, 8)
        store.onVisiblePageChanged(0, 3, 8)

        store.reset()

        assertEquals(1, store.generation)
        assertFalse(store.index.value.isComplete)
        assertEquals(emptyMap<Int, Int>(), store.index.value.pagesByResource)
        assertNull(store.currentPage.value)
    }

    @Test
    fun `measurement callback is assigned to the active reading-order occurrence`() = runBlocking {
        val store = WholeBookPageIndexStore(resourceCount = 3)
        // The same href can occur more than once in an EPUB spine. The
        // scanner's active occurrence, rather than a first-href lookup,
        // determines which resource receives the page count.
        store.expectMeasurement(resourceIndex = 2, href = "shared.xhtml")
        store.publishMeasurement(href = "unrelated.xhtml", totalPages = 4)
        store.publishMeasurement(href = "shared.xhtml#fragment", totalPages = 7)

        val event = store.measurements.first()
        assertEquals(2, event.resourceIndex)
        assertEquals("shared.xhtml", event.href)
        assertEquals(7, event.totalPages)
    }

    @Test
    fun `resource callbacks carry a generation and increasing sequence`() = runBlocking {
        val store = WholeBookPageIndexStore(resourceCount = 2)
        store.expectMeasurement(resourceIndex = 0, href = "chapter-a.xhtml")
        store.publishMeasurement(href = "chapter-a.xhtml", totalPages = 8)
        val first = store.measurements.first()

        store.reset()
        store.expectMeasurement(resourceIndex = 1, href = "chapter-b.xhtml")
        store.publishMeasurement(href = "chapter-b.xhtml", totalPages = 6)
        val second = store.measurements.first { it.generation == 1 }

        assertEquals(0, first.resourceIndex)
        assertEquals(1, second.resourceIndex)
        assertEquals(0, first.generation)
        assertEquals(1, second.generation)
        assertEquals(1L, first.sequence)
        assertEquals(2L, second.sequence)
    }
}
