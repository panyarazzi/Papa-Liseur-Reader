package com.chmouel.liseur.reader.fonts

import com.chmouel.liseur.data.settings.fonts.UserFont
import com.chmouel.liseur.reader.UserFontResources
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.resource.Resource

/** Applies per-book font replacements while Readium reads author styles. */
internal class FontReplacementContainer(
    private val delegate: Container<Resource>,
    private val replacements: () -> Map<String, String>,
    private val fonts: () -> List<UserFont>,
) : Container<Resource> {

    override val entries: Set<Url> get() = delegate.entries

    override fun get(url: Url): Resource? {
        val href = url.toString().substringBefore('#').substringBefore('?')
        val css = href.endsWith(".css", ignoreCase = true)
        val html = href.endsWith(".xhtml", ignoreCase = true) ||
            href.endsWith(".html", ignoreCase = true) ||
            href.endsWith(".htm", ignoreCase = true)
        if (!css && !html) return null

        val resource = delegate.get(url) ?: return null
        val active = replacements().mapNotNull { (family, id) ->
            val font = fonts().firstOrNull { it.id == id } ?: return@mapNotNull null
            family.trim().takeIf(String::isNotEmpty)?.let { it to font }
        }
        if (active.isEmpty()) return resource

        return RewrittenTextResource(resource) { source ->
            if (css) rewriteCss(source, active) else rewriteHtml(source, active)
        }
    }

    override fun close() = Unit

    private fun rewriteCss(source: String, active: List<Pair<String, UserFont>>): String =
        rewriteRules(source, active) + "\n" + fontFaceDeclarations(active)

    private fun rewriteHtml(source: String, active: List<Pair<String, UserFont>>): String {
        val styleBlock = Regex("""(?is)(<style\b[^>]*>)(.*?)(</style\s*>)""")
        var html = styleBlock.replace(source) { match ->
            match.groupValues[1] + rewriteRules(match.groupValues[2], active) + match.groupValues[3]
        }

        // Rewrite inline CSS without touching prose or unrelated markup.
        val styleAttribute = Regex("""(?is)(\bstyle\s*=\s*)(["'])(.*?)\2""")
        html = styleAttribute.replace(html) { match ->
            match.groupValues[1] + match.groupValues[2] +
                rewriteRules(match.groupValues[3], active) + match.groupValues[2]
        }

        val injected = "<style data-liseur-font-replacements>\n${fontFaceDeclarations(active)}\n</style>"
        val headClose = Regex("""(?is)</head\s*>""")
        return if (headClose.containsMatchIn(html)) {
            headClose.replaceFirst(html, "${injected}</head>")
        } else {
            injected + html
        }
    }

    private fun rewriteRules(source: String, active: List<Pair<String, UserFont>>): String {
        val familyDeclaration = Regex("""(?is)font-family\s*:\s*([^;{}]+)""")
        val faceBlock = Regex("""(?is)@font-face\s*\{[^}]*}""")
        val remainingFaces = faceBlock.replace(source) { block ->
            val declaration = familyDeclaration.find(block.value)?.groupValues?.get(1)
            val faceFamily = declaration?.trim()?.unquoteFamily()
            if (active.any { it.first.equals(faceFamily, ignoreCase = true) }) "" else block.value
        }

        return familyDeclaration.replace(remainingFaces) { match ->
            val candidates = splitFamilyList(match.groupValues[1])
            val mapped = candidates.map { candidate ->
                val replacement = active.firstOrNull {
                    it.first.equals(candidate.trim().unquoteFamily(), ignoreCase = true)
                }
                replacement?.let { "\"LiseurReplacement-${it.second.digest}\"" } ?: candidate
            }
            "font-family: " + mapped.joinToString(", ")
        }
    }

    private fun fontFaceDeclarations(active: List<Pair<String, UserFont>>): String =
        active.joinToString("\n") { (_, font) ->
            val weight = font.staticWeight.coerceIn(100, 900)
            val style = if (font.italic) "italic" else "normal"
            """@font-face { font-family: "LiseurReplacement-${font.digest}"; src: url("${UserFontResources.url(font)}"); font-weight: $weight; font-style: $style; }"""
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

private class RewrittenTextResource(
    private val delegate: Resource,
    private val rewrite: (String) -> String,
) : Resource by delegate {
    override suspend fun read(range: LongRange?) =
        delegate.read(range).map { bytes ->
            if (range != null) bytes else rewrite(bytes.decodeToString()).encodeToByteArray()
        }
}
