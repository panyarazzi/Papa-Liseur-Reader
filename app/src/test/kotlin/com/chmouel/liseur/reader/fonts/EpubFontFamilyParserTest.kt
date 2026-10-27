package com.chmouel.liseur.reader.fonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubFontFamilyParserTest {

    @Test
    fun findsPublisherFamiliesAndSeparatesDeclarationsFromUse() {
        val css = """
            /* font-family: CommentedOut; */
            @font-face {
                font-family: THSarabunNewest;
                src: url("../Fonts/THSarabunNewest2.otf");
            }
            @font-face {
                font-family: PSL151;
                src: url("../Fonts/PSL151.ttf");
            }
            body { font-family: THSarabunNewest, sans-serif; }
            h1, h2 { font-family: "PSL162", serif; }
            strong { font-family: PSL162; }
            em { font-family: PSL147, sans-serif; }
        """.trimIndent()

        val result = EpubFontFamilyParser.parse(listOf(css), emptyList())
        val byName = result.associateBy { it.name }

        assertEquals(listOf("THSarabunNewest", "PSL151", "PSL162", "PSL147"), result.map { it.name })
        assertTrue(byName.getValue("THSarabunNewest").declaredByFontFace)
        assertTrue(byName.getValue("THSarabunNewest").referencedByStyles)
        assertTrue(byName.getValue("PSL151").declaredByFontFace)
        assertFalse(byName.getValue("PSL151").referencedByStyles)
        assertFalse(byName.getValue("PSL162").declaredByFontFace)
        assertTrue(byName.getValue("PSL162").referencedByStyles)
        assertFalse(result.any { it.name == "sans-serif" || it.name == "serif" })
    }

    @Test
    fun readsEmbeddedStyleBlocksAndInlineStyle() {
        val xhtml = """
            <html>
              <head><style>
                .chapter { font-family: 'PSL162', sans-serif; }
              </style></head>
              <body>
                <p style="font-family: \"THSarabunNewest\", serif">Text</p>
                <p style='font-family: PSL161'>Emphasis</p>
              </body>
            </html>
        """.trimIndent()

        val result = EpubFontFamilyParser.parse(emptyList(), listOf(xhtml))

        assertEquals(
            listOf("PSL162", "THSarabunNewest", "PSL161"),
            result.map { it.name },
        )
        assertTrue(result.all { it.referencedByStyles })
    }

    @Test
    fun mergesDuplicateNamesWithoutLosingFirstSpelling() {
        val result = EpubFontFamilyParser.parse(
            stylesheets = listOf(
                "p { font-family: PSL162; }",
                "h1 { font-family: psl162; }",
            ),
            documents = emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals("PSL162", result.single().name)
        assertTrue(result.single().referencedByStyles)
    }
}
