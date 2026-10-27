package com.chmouel.liseur.reader.fonts

import com.chmouel.liseur.data.settings.fonts.UserFont
import com.chmouel.liseur.reader.UserFontResources
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.resource.Resource

/**
 * Applies per-publication family replacements at the resource boundary.
 *
 * The EPUB remains untouched on disk. CSS resources are transformed as
 * Readium reads them, and each selected replacement is served from the
 * already-existing user-font container. The replacement map is read on
 * every request so a settings change is picked up when the navigator
 * reloads its document.
 */
internal class FontReplacementContainer(
    private val delegate: Container<Resource>,
    private val replacements: () -> Map<String, String>,
    private val fonts: () -> List<UserFont>,
) : Container<Resource> {

    override val entries: Set<Url> get() = delegate.entries

    override fun get(url: Url): Resource? {
        val href = url.toString().substringBefore('#').substringBefore('?')
        if (!href.endsWith(".css", ignoreCase = true)) return null
        val resource = delegate.get(url) ?: return null
        val active = replacements()
            .mapNotNull { (family, id) ->
                val font = fonts().firstOrNull { it.id == id } ?: return@mapNotNull null
                family.trim().takeIf(String::isNotEmpty)?.let { it to font }
            }
        if (active.isEmpty()) return resource
        return RewrittenCssResource(resource) { original ->
            rewrite(original, active)
        }
    }

    override fun close() = Unit

    private fun rewrite(
        source: String,
        active: List<Pair<String, UserFont>>,
    ): String {
        // Rewrite only declarations; class names, content strings, and
        // unrelated CSS properties must not be touched.
        val familyDeclaration = Regex("""(?is)(font-family\\s*:\\s*)([^;{}]+)""")
        val rewritten = familyDeclaration.replace(source) { match ->
            val list = splitFamilyList(match.groupValues[2])
            val mapped = list.map { candidate ->
                val replacement = active.firstOrNull {
                    it.first.equals(candidate.trim().unquoteFamily(), ignoreCase = true)
                }
                replacement?.let { "LiseurReplacement-${it.second.digest}" } ?: candidate
            }
            match.groupValues[1] + mapped.joinToString(", ")
        }

        val declarations = active.joinToString("\\n") { (family, font) ->
            val safeFamily = family.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"")
            """@font-face { font-family: "LiseurReplacement-${font.digest}"; src: url("${UserFontResources.url(font)}"); font-weight: normal; font-style: normal; }"""
        }
        return "/* Liseur per-family font replacements */\\n$declarations\\n$rewritten"
    }

    private fun splitFamilyList(value: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        value.forEach { c ->
            when {
                quote != null && c == quote -> { current.append(c); quote = null }
                quote == null && (c == '\\"' || c == '\\'') -> { current.append(c); quote = c }
                quote == null && c == ',' -> { values += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        values += current.toString()
        return values
    }

    private fun String.unquoteFamily(): String =
        trim().removeSurrounding("\"").removeSurrounding("'").trim()
}

/** Delegates metadata and close semantics, changing only full CSS reads. */
private class RewrittenCssResource(
    private val delegate: Resource,
    private val rewrite: (String) -> String,
) : Resource by delegate {
    override suspend fun read(range: LongRange?) =
        delegate.read(range).map { bytes ->
            if (range != null) bytes else rewrite(bytes.decodeToString()).encodeToByteArray()
        }
}
