package com.chmouel.liseur.reader.footnotes

/**
 * What counts as a note, in one place.
 *
 * Two things need this answer and they must not disagree. [FootnoteResolver]
 * asks it of a Jsoup document, off the main thread, to decide whether a tap
 * pops a card up or throws the reader into another chapter. [FootnoteLayout]
 * asks it of the live DOM, inside the book's own web view, to decide what to
 * hide. A book where one says yes and the other says no is a book that shows
 * the note twice or not at all, so the vocabulary lives here and both read it
 * from the same set.
 */
internal object NoteVocabulary {

    /** `epub:type` words that name a note. */
    val NOTE_TYPES: Set<String> = setOf("footnote", "endnote", "rearnote", "note")

    /** ARIA roles that name a note. */
    val NOTE_ROLES: Set<String> = setOf("doc-footnote", "doc-endnote")

    /** The element a note is written as when it is not labelled at all. */
    const val NOTE_TAG: String = "aside"

    /** EPUB2 converters commonly put notes in list items with a return link. */
    const val LEGACY_LIST_NOTE_TAG: String = "li"

    /** Classes used by EPUB2 generators for the link back to the citation. */
    val BACKLINK_CLASSES: Set<String> = setOf("back-link", "backlink")

    /** Semantic spellings of a backlink, where a class is not supplied. */
    val BACKLINK_TYPES: Set<String> = setOf("backlink")
    val BACKLINK_ROLES: Set<String> = setOf("doc-backlink")

    /** `epub:type` words that name a reference *to* a note. */
    val REF_TYPES: Set<String> = setOf("noteref")

    /** ARIA roles that name a reference to a note. */
    val REF_ROLES: Set<String> = setOf("doc-noteref")

    fun isNote(epubType: String, role: String, tagName: String): Boolean =
        names(epubType, NOTE_TYPES) ||
            names(role, NOTE_ROLES) ||
            tagName.lowercase() == NOTE_TAG

    /**
     * EPUB2 often has no semantic note marker. A list item is accepted only
     * when it contains an explicit backlink to the citation, avoiding the
     * unsafe rule that every identified list item is a footnote.
     */
    fun isLegacyListNote(
        tagName: String,
        backlinkClasses: Set<String>,
        backlinkEpubType: String,
        backlinkRole: String,
    ): Boolean =
        tagName.lowercase() == LEGACY_LIST_NOTE_TAG &&
            (backlinkClasses.any { it.lowercase() in BACKLINK_CLASSES } ||
                names(backlinkEpubType, BACKLINK_TYPES) ||
                names(backlinkRole, BACKLINK_ROLES))

    /** Whether an anchor carrying [epubType] and [role] points at a note. */
    fun isNoteRef(epubType: String, role: String): Boolean =
        names(epubType, REF_TYPES) || names(role, REF_ROLES)

    private fun names(attribute: String, vocabulary: Set<String>): Boolean =
        attribute.split(WHITESPACE).any { it.substringAfterLast(':') in vocabulary }

    private val WHITESPACE = Regex("\\s+")
}
