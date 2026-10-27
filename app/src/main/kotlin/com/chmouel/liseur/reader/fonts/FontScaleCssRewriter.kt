package com.chmouel.liseur.reader.fonts

/**
 * Scales only CSS rules which explicitly name an EPUB font family.
 *
 * Numeric CSS font-size values are scaled numerically to avoid relying on newer CSS calc()
 * multiplication support in WebView. For a family-specific rule without font-size, a relative
 * em size is added. Keyword/calc font sizes are preserved because rewriting them safely requires
 * evaluating the cascade; this is deliberately preferable to corrupting publisher CSS.
 */
internal object FontScaleCssRewriter {

    fun rewrite(source: String, scales: List<Pair<String, Double>>): String {
        if (scales.isEmpty()) return source
        val rule = Regex("""(?s)([^{}]+)\{([^{}]*)\}""")
        return rule.replace(source) { match ->
            val prelude = match.groupValues[1]
            val declarations = match.groupValues[2]
            if (prelude.trimStart().startsWith("@")) match.value
            else prelude + "{" + scaleDeclarations(declarations, scales) + "}"
        }
    }

    fun scaleInlineStyle(declarations: String, scales: List<Pair<String, Double>>): String =
        scaleDeclarations(declarations, scales)

    private fun scaleDeclarations(
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
                val numeric = Regex("""^([-+]?(?:\d+(?:\.\d*)?|\.\d+))([a-zA-Z%]*)$""").matchEntire(value)
                if (numeric == null) match.value
                else {
                    val amount = numeric.groupValues[1].toDouble() * scale
                    val formatted = "%.4f".format(java.util.Locale.ROOT, amount)
                        .trimEnd('0').trimEnd('.')
                    "${match.groupValues[1]}${formatted}${numeric.groupValues[2]}${important}"
                }
            }
        }

        val relativeScale = "%.3f".format(java.util.Locale.ROOT, scale)
            .trimEnd('0').trimEnd('.')
        return declarations.trimEnd() + "\nfont-size: ${relativeScale}em;\n"
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
