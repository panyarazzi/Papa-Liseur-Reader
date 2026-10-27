package com.chmouel.liseur.reader.fonts

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

/**
 * Reads the author styles of a publication and discovers its font families.
 *
 * CSS is collected from the publication's resource list; XHTML is collected
 * from its reading order so embedded style blocks and inline styles count too.
 * Unreadable optional resources are skipped: one malformed stylesheet must
 * not stop a reader opening an otherwise readable book. Discovery is bounded
 * so a large EPUB or a resource that never completes cannot hold the settings
 * screen indefinitely.
 */
internal object EpubFontInspector {

    private const val TAG = "EpubFontInspector"
    private const val MAX_PARALLEL_READS = 8
    private const val RESOURCE_TIMEOUT_MS = 1_500L
    private const val GROUP_BUDGET_MS = 6_000L

    suspend fun inspect(publication: Publication): List<EpubFontFamily> =
        withContext(Dispatchers.IO) {
            val stylesheets = readResources(
                publication,
                publication.resources.filter(::looksLikeStylesheet),
            )
            val documents = readResources(
                publication,
                publication.readingOrder.filter(::looksLikeHtmlDocument),
            )
            try {
                EpubFontFamilyParser.parse(stylesheets, documents)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not parse EPUB author styles (${error.javaClass.simpleName})", error)
                emptyList()
            } catch (error: LinkageError) {
                // Preserve the underlying cause if a parser object fails during initialization.
                Log.e(TAG, "Font parser initialization failed (${error.javaClass.simpleName})", error)
                emptyList()
            }
        }

    private suspend fun readResources(
        publication: Publication,
        links: List<Link>,
    ): List<String> {
        if (links.isEmpty()) return emptyList()
        val collected = mutableListOf<String>()

        // Process bounded batches rather than launching one coroutine for every
        // chapter in a book with hundreds or thousands of XHTML resources.
        withTimeoutOrNull(GROUP_BUDGET_MS) {
            for (batch in links.chunked(MAX_PARALLEL_READS)) {
                val contents = coroutineScope {
                    batch.map { link ->
                        async {
                            withTimeoutOrNull(RESOURCE_TIMEOUT_MS) {
                                publication.readText(link)
                            }
                        }
                    }.awaitAll()
                }
                collected += contents.filterNotNull()
            }
        }
        return collected
    }

    private suspend fun Publication.readText(link: Link): String? {
        return try {
            val resource = get(link) ?: return null
            resource.read().getOrNull()?.decodeToString()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.d(TAG, "Skipping unreadable EPUB resource ${link.href}: ${error.javaClass.simpleName}")
            null
        } catch (error: LinkageError) {
            Log.w(TAG, "Skipping EPUB resource after initialization failure ${link.href}", error)
            null
        }
    }

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
