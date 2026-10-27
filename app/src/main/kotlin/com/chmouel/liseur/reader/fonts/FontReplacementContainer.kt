package com.chmouel.liseur.reader.fonts

import com.chmouel.liseur.data.settings.fonts.UserFont
import com.chmouel.liseur.reader.UserFontResources
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.resource.Resource

/**
 * Applies per-book font replacement and family-scale rules while Readium reads author styles.
 *
 * Scaling is applied to the CSS rule which declares a family, rather than globally to the
 * document. This preserves unrelated families and lets the reader compensate for differences
 * between Thai font metrics. EPUB styles are varied, so this remains a best-effort CSS rewrite.
 */
internal class FontReplacementContainer(
    private val delegate: Container<Resource>,
    private val replacements: () -> Map<String, String>,
    private val scales: () -> Map<String, Double>,
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
        val activeScales = scales().mapNotNull { (family, scale) ->
            family.trim().takeIf(String::isNotEmpty)?.let {
                it to scale.takeIf { value -> value.isFinite() }?.coerceIn(0.5, 2.0)
            }
        }.mapNotNull { (family, scale) -> scale?.let { family to it } }

        if (active.isEmpty() && activeScales.isEmpty()) return resource

        return RewrittenTextResource(resource) { source ->
            if (css) rewriteCss(source, active, activeScales)
            else rewriteHtml(source, active, activeScales)
        }
    }

    override fun close() = Unit

    private fun rewriteCss(
        source: String,
        active: List<Pair<String, UserFont>>,
        scales: List<Pair<String, Double>>,
    ): String {
        val rewritten = rewriteRules(source, active, scales)
        return if (active.isEmpty()) rewritten else rewritten + "\n" + fontFaceDeclarations(active)
    }

    private fun rewriteHtml(
        source: String,
        active: List<Pair<String, UserFont>>,
        scales: List<Pair<String, Double>>,
    ): String {
        val styleBlock = Regex("""(?is)(<style\b[^>]*>)(.*?)(</style\s*>)""")
        var html = styleBlock.replace(source) { match ->
            match.groupValues[1] + rewriteRules(match.groupValues[2], active, scales) + match.groupValues[3]
        }

        // Inline style has no selector block, so scale its declarations directly.
        val styleAttribute = Regex("""(?is)(\bstyle\s*=\s*)(["'])(.*?)\2""")
        html = styleAttribute.replace(html) { match ->
            val original = match.groupValues[3]
            val scaled = scaleDeclarationBlock(original, scales)
            val rewritten = rewriteFamilyDeclarations(scaled, active)
            match.groupValues[1] + match.groupValues[2] + rewritten + match.groupValues[2]
        }

        if (active.isEmpty()) return html
        val injected = "<style data-liseur-font-replacements>\n${fontFaceDeclarations(active)}\n</style>"
        val headClose = Regex("""(?is)</head\s*>""")
        return if (headClose.containsMatchIn(html)) {
            headClose.replaceFirst(html, "${injected}</head>")
        } else {
            injected + html
        }
    }

    private fun rewriteRules(
        source: String,
        active: List<Pair<String, UserFont>>,
        scales: List<Pair<String, Double>>,
    ): String {
        val rule = Regex("""(?s)([^{}]+)\{([^{}]*)\}""")
        val scaled = rule.replace(source) { match ->
            val prelude = match.groupValues[1]
            val body = match.groupValues[2]
            if (prelude.trimStart().startsWith("@")) match.value
            else prelude + "{" + scaleDeclarationBlock(body, scales) + "}"
        }
        return rewriteFamilyDeclarations(scaled, active)
    }

    private fun scaleDeclarationBlock(
        declarations: String,
        scales: List<Pair<String, Double>>,
    ): String {
        if (scales.isEmpty()) return declarations
        val familyValue = Regex("""(?is)font-family\s*:\s*([^;{}]+)""")
            .find(declarations)?.groupValues?.get(1) ?: return declarations
        val candidates = splitFamilyList(familyValue).map { it.trim().unquoteFamily() }
        val scale = candidates.firstNotNullOfOrNull { candidate ->
            scales.firstOrNull { it.first.equals(candidate, ignoreCase = true) }?.second
        } ?: return declarations

        val sizePattern = Regex("""(?is)(font-size\s*:\s*)([^;]+)""")
        if (sizePattern.containsMatchIn(declarations)) {
            return sizePattern.replace(declarations) { match ->
                val raw = match.groupValues[2].trim()
                val important = Regex("""(?i)\s*!important\s*$""").find(raw)?.value.orEmpty()
                val value = raw.removeSuffix(important).trim()
                "${match.groupValues[1]}calc((${value}) * ${scale})${important}"
            }
        }
        // A family-specific rule without a size still needs a local multiplier.
        return declarations.trimEnd() + "\nfont-size: calc(1em * ${scale});\n"
    }

    private fun rewriteFamilyDeclarations(
        source: String,
        active: List<Pair<String, UserFont>>,
    ): String {
        if (active.isEmpty()) return source
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
