package com.chmouel.liseur.reader.fonts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

/**
 * Reads the author styles of a publication and discovers its font families.
 *
 * CSS is collected from the publication's resource list; XHTML is collected
 * from its reading order so embedded style blocks and inline styles count too.
 * Unreadable optional resources are skipped: one malformed stylesheet must
 * not stop a reader opening an otherwise readable book.
 */
internal object EpubFontInspector {

    suspend fun inspect(publication: Publication): List<EpubFontFamily> =
        withContext(Dispatchers.IO) {
            val stylesheets = publication.resources
                .filter(::looksLikeStylesheet)
                .mapNotNull { link -> publication.readText(link) }

            val documents = publication.readingOrder
                .filter(::looksLikeHtmlDocument)
                .mapNotNull { link -> publication.readText(link) }

            EpubFontFamilyParser.parse(stylesheets, documents)
        }

    private suspend fun Publication.readText(link: Link): String? =
        runCatching {
            val resource = get(link).getOrNull() ?: return@runCatching null
            resource.read().getOrNull()?.decodeToString()
        }.getOrNull()

    private fun looksLikeStylesheet(link: Link): Boolean {
        val address = link.url().toString().substringBefore('#').substringBefore('?')
        return address.endsWith(".css", ignoreCase = true) ||
            link.mediaType?.toString()?.contains("css", ignoreCase = true) == true
    }

    private fun looksLikeHtmlDocument(link: Link): Boolean {
        val address = link.url().toString().substringBefore('#').substringBefore('?')
        val mediaType = link.mediaType?.toString().orEmpty()
        return address.endsWith(".xhtml", ignoreCase = true) ||
            address.endsWith(".html", ignoreCase = true) ||
            address.endsWith(".htm", ignoreCase = true) ||
            mediaType.contains("xhtml", ignoreCase = true) ||
            mediaType.contains("html", ignoreCase = true)
    }
}
