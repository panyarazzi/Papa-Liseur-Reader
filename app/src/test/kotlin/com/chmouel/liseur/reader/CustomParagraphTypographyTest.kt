package com.chmouel.liseur.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomParagraphTypographyTest {
    @Test
    fun readsFractionalComputedMetricsWithoutRounding() {
        val metrics = CustomParagraphTypography.parseMetrics("\"1.15|0.75\"")
        assertNotNull(metrics)
        assertEquals(1.15, metrics!!.lineHeight!!, 1e-9)
        assertEquals(0.75, metrics.textIndent!!, 1e-9)
    }

    @Test
    fun missingVisibleParagraphDoesNotInventABaseline() {
        assertNull(CustomParagraphTypography.parseMetrics("\"||\""))
    }

    @Test
    fun nudgeChangesValuesByOneTenthAndRetainsManualFractions() {
        assertEquals(1.25, CustomParagraphTypography.nudge(1.15, 1), 1e-9)
        assertEquals(1.05, CustomParagraphTypography.nudge(1.15, -1), 1e-9)
        assertEquals(0.85, CustomParagraphTypography.nudge(0.75, 1), 1e-9)
    }

    @Test
    fun customValuesRejectInvalidNumbersWithoutRoundingValidDecimals() {
        assertEquals(1.15, CustomParagraphTypography.sanitizeLineHeight(1.15)!!, 1e-9)
        assertNull(CustomParagraphTypography.sanitizeLineHeight(Double.NaN))
        assertNull(CustomParagraphTypography.sanitizeLineHeight(11.0))
        assertEquals(0.75, CustomParagraphTypography.sanitizeTextIndent(0.75)!!, 1e-9)
    }

    @Test
    fun noSelectedCustomValueLeavesPublisherCssAlone() {
        val script = CustomParagraphTypography.applyScript(null, null)
        assertTrue(script.contains("if (!line && !indent)"))
        assertTrue(script.contains("if (old) old.remove()"))
        assertFalse(script.contains("line-height: !important"))
        assertFalse(script.contains("text-indent:em !important"))
    }

    @Test
    fun customRulesTargetOnlyParagraphLineHeightAndFirstLineIndent() {
        val script = CustomParagraphTypography.applyScript(1.15, 0.75)
        assertTrue(script.contains("var line = '1.15'"))
        assertTrue(script.contains("var indent = '0.75'"))
        assertTrue(script.contains("html body p:not(#papa-custom-paragraph-typography)"))
        assertTrue(CustomParagraphTypography.READ_METRICS_SCRIPT.contains("range.getClientRects()"))
        assertFalse(script.contains("margin"))
        assertFalse(script.contains("letter-spacing"))
    }
}
