package com.chmouel.liseur.reader.footnotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootnoteResolverTest {

    private val standardEbooks = """
        <html xmlns:epub="http://www.idpf.org/2007/ops">
          <body>
            <section id="endnotes" epub:type="endnotes footnotes">
              <ol>
                <li id="note-1" epub:type="endnote footnote">
                  <p>Is that my governess?
                    <a href="chapter-11.xhtml#noteref-1" epub:type="backlink">↩︎</a>
                  </p>
                </li>
              </ol>
            </section>
          </body>
        </html>
    """.trimIndent()

    @Test
    fun `finds an endnote marked with epub type`() {
        val note = FootnoteResolver.noteAt(standardEbooks, "note-1")!!
        assertTrue(note, note.contains("Is that my governess?"))
    }

    @Test
    fun `finds a note marked only with an ARIA role`() {
        val html = """<body><div id="fn3" role="doc-endnote"><p>A note by role alone.</p></div></body>"""
        val note = FootnoteResolver.noteAt(html, "fn3")!!
        assertTrue(note, note.contains("A note by role alone."))
    }

    @Test
    fun `finds a note in an aside`() {
        val html = """<body><aside id="fn7"><p>Set in an aside.</p></aside></body>"""
        val note = FootnoteResolver.noteAt(html, "fn7")!!
        assertTrue(note, note.contains("Set in an aside."))
    }

    @Test
    fun `finds the EPUB2 list item with a back-link class`() {
        val html = """
            <html><body><ol>
              <li id="fn_2">
                ฝู่เป็นเขตปกครอง...
                <a class="back-link" href="chapter.xhtml#fnref_2">↩</a>
                <span class="footnote-chapter">บทที่ 1</span>
              </li>
            </ol></body></html>
        """.trimIndent()
        val note = FootnoteResolver.noteAt(html, "fn_2")!!
        assertTrue(note, note.contains("ฝู่เป็นเขตปกครอง"))
        assertTrue(note, note.contains("footnote-chapter"))
    }

    @Test
    fun `finds an EPUB2 list item with a semantic backlink`() {
        val html = """<li id="fn2"><p>Note text <a epub:type="backlink" href="chapter.xhtml#ref2">↩</a></p></li>"""
        assertTrue(FootnoteResolver.noteAt(html, "fn2")!!.contains("Note text"))
    }

    @Test
    fun `a plain list item is not treated as a footnote`() {
        val html = """<ol><li id="item2"><p>An ordinary list item.</p></li></ol>"""
        assertNull(FootnoteResolver.noteAt(html, "item2"))
    }

    @Test
    fun `a chapter is not a note`() {
        val html = """<section id="chapter-4" epub:type="chapter"><p>Long ago.</p></section>"""
        assertNull(FootnoteResolver.noteAt(html, "chapter-4"))
    }

    @Test
    fun `a paragraph a cross-reference points at is not a note`() {
        val html = """<p id="para-9">See above.</p>"""
        assertNull(FootnoteResolver.noteAt(html, "para-9"))
    }

    @Test
    fun `a fragment naming nothing resolves to nothing`() {
        assertEquals(null, FootnoteResolver.noteAt(standardEbooks, "note-404"))
    }

    @Test
    fun `an empty note is not worth popping up`() {
        val html = """<aside id="fn1">   </aside>"""
        assertNull(FootnoteResolver.noteAt(html, "fn1"))
    }

    @Test
    fun `scripts do not survive the trip out of the book`() {
        val html = """<aside id="fn1"><p>Safe.</p><script>alert(1)</script></aside>"""
        val note = FootnoteResolver.noteAt(html, "fn1")!!
        assertTrue(note, note.contains("Safe."))
        assertTrue(note, !note.contains("alert"))
    }

    @Test
    fun `the note keeps its own markup`() {
        val html = """<aside id="fn1"><p>An <em>emphatic</em> note.</p></aside>"""
        val note = FootnoteResolver.noteAt(html, "fn1")!!
        assertTrue(note, note.contains("<em>emphatic</em>"))
    }

    @Test
    fun `unparseable input is answered with nothing rather than a crash`() {
        assertEquals(null, FootnoteResolver.noteAt("", "fn1"))
    }
}
