package com.chmouel.liseur.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookCustomTypographyTest {
    @Test
    fun customValuesAreKeptIndependentlyForEachBook() {
        val first = BookCustomTypography("book-a", 1.15, 0.75).sanitized()
        val second = BookCustomTypography("book-b", null, 1.0).sanitized()
        assertEquals(1.15, first.lineHeight!!, 1e-9)
        assertEquals(0.75, first.textIndent!!, 1e-9)
        assertNull(second.lineHeight)
        assertEquals(1.0, second.textIndent!!, 1e-9)
    }

    @Test
    fun invalidCustomValuesAreDiscardedButValidDecimalsRemain() {
        val safe = BookCustomTypography("book", Double.NaN, 0.75).sanitized()
        assertNull(safe.lineHeight)
        assertEquals(0.75, safe.textIndent!!, 1e-9)
    }
}
