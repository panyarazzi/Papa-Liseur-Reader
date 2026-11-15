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
    fun `resource callbacks carry a generation and increasing sequence`() = runBlocking {
        val store = WholeBookPageIndexStore(resourceCount = 2)
        store.publishMeasurement("chapter-a.xhtml", 8)
        val first = store.measurements.first()

        store.reset()
        store.publishMeasurement("chapter-b.xhtml", 6)
        val second = store.measurements.first { it.generation == 1 }

        assertEquals(0, first.generation)
        assertEquals(1, second.generation)
        assertEquals(1L, first.sequence)
        assertEquals(2L, second.sequence)
    }
}
