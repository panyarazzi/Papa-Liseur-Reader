package com.chmouel.liseur.reader.fonts

import com.chmouel.liseur.data.settings.fonts.UserFont
import com.chmouel.liseur.reader.UserFontResources
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.resource.Resource

/**
 * Applies per-book font replacements while Readium reads author CSS.
 *
 * Only CSS is transformed; the original EPUB on disk is never modified.
 * Replaced @font-face declarations are removed so the embedded original
 * face cannot win over the user's selected face at the same family name.
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
        val active = replacements().mapNotNull { (family, id) ->
            val font = fonts().firstOrNull { it.id == id } ?: return@mapNotNull null
            family.trim().takeIf(String::isNotEmpty)?.let { it to font }
        }
        if (active.isEmpty()) return resource
        return RewrittenCssResource(resource) { source -> rewrite(source, active) }
    }

    override fun close() = Unit

    private fun rewrite(source: String, active: List<Pair<String, UserFont>>): String {
        val familyDeclaration = Regex("""(?is)font-family\s*:\s*([^;{}]+)""")
        val faceBlock = Regex("""(?is)@font-face\s*\{[^}]*}""")
        val remainingFaces = faceBlock.replace(source) { block ->
            val declaration = familyDeclaration.find(block.value)?.groupValues?.get(1)
            val faceFamily = declaration?.trim()?.unquoteFamily()
            if (active.any { it.first.equals(faceFamily, ignoreCase = true) }) "" else block.value
        }

        val rewritten = familyDeclaration.replace(remainingFaces) { match ->
            val candidates = splitFamilyList(match.groupValues[1])
            val mapped = candidates.map { candidate ->
                val replacement = active.firstOrNull {
                    it.first.equals(candidate.trim().unquoteFamily(), ignoreCase = true)
                }
                replacement?.let { "\"LiseurReplacement-${it.second.digest}\"" } ?: candidate
            }
            "font-family: " + mapped.joinToString(", ")
        }

        val declarations = active.joinToString("\n") { (_, font) ->
            val weight = font.staticWeight.coerceIn(100, 900)
            val style = if (font.italic) "italic" else "normal"
            """@font-face { font-family: "LiseurReplacement-${font.digest}"; src: url("${UserFontResources.url(font)}"); font-weight: $weight; font-style: $style; }"""
        }
        return "/* Liseur per-book font replacements */\n$declarations\n$rewritten"
    }

    private fun splitFamilyList(value: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        value.forEach { c ->
            when {
                quote != null && c == quote -> { current.append(c); quote = null }
                quote == null && (c == '"' || c == '\'') -> { current.append(c); quote = c }
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

private class RewrittenCssResource(
    private val delegate: Resource,
    private val rewrite: (String) -> String,
) : Resource by delegate {
    override suspend fun read(range: LongRange?) =
        delegate.read(range).map { bytes ->
            if (range != null) bytes else rewrite(bytes.decodeToString()).encodeToByteArray()
        }
}
