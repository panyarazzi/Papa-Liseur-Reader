package com.chmouel.liseur.reader.footnotes

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

/**
 * Resolves EPUB footnote targets, including the common EPUB2 list-item form.
 */
object FootnoteResolver {

    /**
     * The note identified by [fragment] in [html], or null if there is none.
     * The returned HTML is sanitised before it is shown in the footnote card.
     */
    fun noteAt(html: String, fragment: String): String? {
        val document = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null
        val element = document.getElementById(fragment) ?: return null
        if (!isNote(element)) return null

        val content = element.html()
        if (content.isBlank()) return null
        val safelist = Safelist.relaxed().addAttributes(":all", "class")
        return Jsoup.clean(content, safelist).takeIf { it.isNotBlank() }
    }

    private fun isNote(element: Element): Boolean {
        // Jsoup lowercases attribute names but keeps the namespace prefix.
        val epubType = element.attr("epub:type").ifEmpty { element.attr("type") }
        if (NoteVocabulary.isNote(
                epubType = epubType,
                role = element.attr("role"),
                tagName = element.normalName(),
            )
        ) return true

        // EPUB2 converters commonly emit <li id="..."> with an explicit
        // <a class="back-link"> to the citation instead of epub:type/role.
        // Require that backlink evidence; a plain list item is not enough.
        if (element.normalName() != NoteVocabulary.LEGACY_LIST_NOTE_TAG) return false
        return element.select("a[href]").any { anchor ->
            val classes = anchor.classNames()
            val backlinkType = anchor.attr("epub:type").ifEmpty { anchor.attr("type") }
            NoteVocabulary.isLegacyListNote(
                tagName = element.normalName(),
                backlinkClasses = classes,
                backlinkEpubType = backlinkType,
                backlinkRole = anchor.attr("role"),
            )
        }
    }
}
