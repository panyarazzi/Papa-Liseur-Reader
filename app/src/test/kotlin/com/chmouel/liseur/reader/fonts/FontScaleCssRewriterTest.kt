package com.chmouel.liseur.reader.fonts

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class FontScaleCssRewriterTest {

    @Test
    fun scalesOnlyRulesUsingTheSelectedFamily() {
        val css = """
            .content { font-family: FC-Sarasamkun; font-size: 20px; }
            .heading { font-family: "PSL162", serif; font-size: 16px; }
        """.trimIndent()

        val result = FontScaleCssRewriter.rewrite(
            css,
            listOf("PSL162" to 1.25),
        )

        assertTrue(result.contains("font-size: 20px"))
        assertTrue(result.contains("font-size: 20px"))
        assertTrue(result.contains("font-size: 20px"))
        assertFalse(result.contains("font-size: 16px"))
        assertTrue(result.contains("font-size: 20px"))
    }

    @Test
    fun addsRelativeScaleWhenFamilyRuleHasNoExplicitSize() {
        val result = FontScaleCssRewriter.rewrite(
            ".emphasis { font-family: PSL147; font-style: italic; }",
            listOf("PSL147" to 1.2),
        )

        assertTrue(result.contains("font-size: 1.2em"))
        assertTrue(result.contains("font-style: italic"))
    }

    @Test
    fun scalesInlineStyleAndLeavesUnsupportedKeywordSizeIntact() {
        val inline = FontScaleCssRewriter.scaleInlineStyle(
            "font-family: PSL162; font-size: 120%;",
            listOf("PSL162" to 1.25),
        )
        assertTrue(inline.contains("font-size: 150%"))

        val keyword = FontScaleCssRewriter.scaleInlineStyle(
            "font-family: PSL162; font-size: large;",
            listOf("PSL162" to 1.25),
        )
        assertTrue(keyword.contains("font-size: large"))
    }
}
