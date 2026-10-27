package com.chmouel.liseur.reader.fonts

/**
 * A non-generic font-family name found in an EPUB's author styles.
 *
 * [declaredByFontFace] means a stylesheet declares a matching @font-face.
 * [referencedByStyles] means a CSS font-family declaration or an inline
 * style asks for the family. A declaration can exist without being used.
 */
internal data class EpubFontFamily(
    val name: String,
    val declaredByFontFace: Boolean,
    val referencedByStyles: Boolean,
)

/**
 * Extracts publisher font-family names without changing the publication.
 *
 * This is deliberately a discovery step, not a CSS rewriter. It examines
 * stylesheets and XHTML/HTML supplied by the caller, ignores generic CSS
 * families, preserves the spelling first used by the EPUB, and merges
 * duplicate names case-insensitively. The publication scanner is
 * responsible for loading CSS and document resources from Readium.
 */
internal object EpubFontFamilyParser {

    private val comments = Regex("""(?s)/\\*.*?\\*/""")
    private val htmlComments = Regex("""(?s)<!--.*?-->""")
    private val fontFaceBlocks = Regex("""(?is)@font-face\\s*\\{([^}]*)}""")
    private val fontFamilyDeclarations = Regex("""(?is)font-family\\s*:\\s*([^;{}]+)""")
    private val styleBlocks = Regex("""(?is)<style\\b[^>]*>(.*?)</style\\s*>""")
    private val styleAttributes = Regex("""(?is)\\bstyle\\s*=\\s*(?:"([^"]*)"|'([^']*)')""")

    private val genericFamilies = setOf(
        "serif", "sans-serif", "monospace", "cursive", "fantasy",
        "system-ui", "ui-serif", "ui-sans-serif", "ui-monospace",
        "ui-rounded", "emoji", "math", "fangsong", "inherit", "initial",
        "unset", "revert", "revert-layer",
    )

    fun parse(
        stylesheets: Iterable<String>,
        documents: Iterable<String>,
    ): List<EpubFontFamily> {
        val families = linkedMapOf<String, MutableFamily>()

        fun record(name: String, face: Boolean, referenced: Boolean) {
            val key = name.lowercase(java.util.Locale.ROOT)
            val entry = families.getOrPut(key) { MutableFamily(name) }
            entry.declaredByFontFace = entry.declaredByFontFace || face
            entry.referencedByStyles = entry.referencedByStyles || referenced
        }

        fun scanCss(source: String) {
            val css = source.replace(comments, "")
            fontFaceBlocks.findAll(css).forEach { match ->
                val faceCss = match.groupValues[1]
                fontFamilyDeclarations.findAll(faceCss).forEach { declaration ->
                    splitFamilies(declaration.groupValues[1]).forEach { name ->
                        if (!name.isGeneric()) record(name, face = true, referenced = false)
                    }
                }
            }

            // A family inside @font-face is a declaration, not evidence
            // that the family is used by an element. Scan the remaining
            // rules separately so those two facts remain distinguishable.
            val authorRules = fontFaceBlocks.replace(css, "")
            fontFamilyDeclarations.findAll(authorRules).forEach { declaration ->
                splitFamilies(declaration.groupValues[1]).forEach { name ->
                    if (!name.isGeneric()) record(name, face = false, referenced = true)
                }
            }
        }

        stylesheets.forEach(::scanCss)
        documents.forEach { document ->
            val html = document.replace(htmlComments, "")
            styleBlocks.findAll(html).forEach { scanCss(it.groupValues[1]) }
            styleAttributes.findAll(html).forEach { match ->
                val inlineStyle = match.groups[1]?.value ?: match.groups[2]?.value.orEmpty()
                fontFamilyDeclarations.findAll(inlineStyle).forEach { declaration ->
                    splitFamilies(declaration.groupValues[1]).forEach { name ->
                        if (!name.isGeneric()) record(name, face = false, referenced = true)
                    }
                }
            }
        }

        return families.values.map {
            EpubFontFamily(
                name = it.name,
                declaredByFontFace = it.declaredByFontFace,
                referencedByStyles = it.referencedByStyles,
            )
        }
    }

    private data class MutableFamily(
        val name: String,
        var declaredByFontFace: Boolean = false,
        var referencedByStyles: Boolean = false,
    )

    private fun splitFamilies(value: String): List<String> {
        val result = mutableListOf<String>()
        val part = StringBuilder()
        var quote: Char? = null
        value.forEach { char ->
            when {
                quote != null && char == quote -> quote = null
                quote == null && (char == '"' || char == '\'') -> quote = char
                quote == null && char == ',' -> {
                    part.toString().trim().unquoteCssFamily().takeIf(String::isNotEmpty)?.let(result::add)
                    part.clear()
                }
                else -> part.append(char)
            }
        }
        part.toString().trim().unquoteCssFamily().takeIf(String::isNotEmpty)?.let(result::add)
        return result
    }

    private fun String.unquoteCssFamily(): String =
        trim().removeSurrounding("\"").removeSurrounding("'").trim()

    private fun String.isGeneric(): Boolean =
        isBlank() || lowercase(java.util.Locale.ROOT) in genericFamilies
}
