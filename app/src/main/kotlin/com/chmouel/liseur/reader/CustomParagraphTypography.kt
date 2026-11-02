package com.chmouel.liseur.reader

/** Runtime-only CSS access for per-book paragraph line height and first-line indent. */
internal object CustomParagraphTypography {

    data class Metrics(val lineHeight: Double?, val textIndent: Double?)

    fun parseMetrics(result: String?): Metrics? {
        val value = result?.trim()?.removeSurrounding("\"") ?: return null
        val parts = value.split('|')
        if (parts.size != 2) return null
        fun number(raw: String): Double? = raw.toDoubleOrNull()?.takeIf { it.isFinite() }
        return Metrics(number(parts[0]), number(parts[1]))
    }

    fun sanitizeLineHeight(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in 0.1..10.0 }

    fun sanitizeTextIndent(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in -20.0..20.0 }

    fun nudge(value: Double, direction: Int): Double =
        ((value.toBigDecimal() + ("0.1".toBigDecimal() * direction.toBigDecimal()))
            .stripTrailingZeros()).toDouble()

    fun applyScript(lineHeight: Double?, textIndent: Double?): String {
        val line = sanitizeLineHeight(lineHeight)?.toString() ?: ""
        val indent = sanitizeTextIndent(textIndent)?.toString() ?: ""
        return APPLY_SCRIPT
            .replace("__PAPA_LINE_HEIGHT__", line)
            .replace("__PAPA_TEXT_INDENT__", indent)
    }

    val READ_METRICS_SCRIPT = """
        (function () {
          var paragraphs = Array.prototype.slice.call(document.querySelectorAll('p'));
          var el = paragraphs.find(function (p) {
            if (!p.textContent || !p.textContent.trim()) return false;
            var r = p.getBoundingClientRect();
            return r.bottom > 0 && r.top < window.innerHeight && r.right > 0 && r.left < window.innerWidth;
          });
          if (!el) return '||';
          var css = window.getComputedStyle(el);
          var fontSize = parseFloat(css.fontSize);
          if (!(fontSize > 0)) return '||';
          var lineText = (css.lineHeight || '').trim().toLowerCase();
          var ratio = NaN;
          if (lineText === 'normal' || !lineText) {
            // When normal has no numeric computed value, derive line-box height
            // from consecutive rendered text lines, preserving Thai glyph metrics.
            try {
              var range = document.createRange();
              range.selectNodeContents(el);
              var tops = [];
              Array.prototype.forEach.call(range.getClientRects(), function (rect) {
                if (!tops.some(function (top) { return Math.abs(top - rect.top) < 0.5; })) tops.push(rect.top);
              });
              tops.sort(function (a, b) { return a - b; });
              if (tops.length > 1) ratio = (tops[1] - tops[0]) / fontSize;
            } catch (ignored) {}
            if (!(ratio > 0)) {
              var probe = document.createElement('div');
              probe.textContent = (el.textContent || '').trim().slice(0, 8) || 'Hg';
              probe.style.position = 'absolute';
              probe.style.visibility = 'hidden';
              probe.style.whiteSpace = 'nowrap';
              probe.style.display = 'inline-block';
              probe.style.fontFamily = css.fontFamily;
              probe.style.fontSize = css.fontSize;
              probe.style.fontWeight = css.fontWeight;
              probe.style.fontStyle = css.fontStyle;
              probe.style.fontStretch = css.fontStretch;
              probe.style.lineHeight = 'normal';
              (document.body || document.documentElement).appendChild(probe);
              ratio = probe.getBoundingClientRect().height / fontSize;
              probe.remove();
            }
          } else {
            var lineNumber = parseFloat(lineText);
            if (isFinite(lineNumber)) {
              if (lineText.endsWith('px')) ratio = lineNumber / fontSize;
              else if (lineText.endsWith('rem')) {
                var rootSize = parseFloat(window.getComputedStyle(document.documentElement).fontSize) || fontSize;
                ratio = lineNumber * rootSize / fontSize;
              } else if (lineText.endsWith('em')) ratio = lineNumber;
              else if (lineText.endsWith('%')) ratio = lineNumber / 100;
              else ratio = lineNumber;
            }
          }
          var indentText = (css.textIndent || '0px').trim().toLowerCase();
          var indentNumber = parseFloat(indentText);
          var indentPixels = 0;
          if (isFinite(indentNumber)) {
            if (indentText.endsWith('%')) {
              var parentWidth = el.parentElement ? el.parentElement.getBoundingClientRect().width : 0;
              indentPixels = parentWidth * indentNumber / 100;
            } else if (indentText.endsWith('rem')) {
              var rootFontSize = parseFloat(window.getComputedStyle(document.documentElement).fontSize) || fontSize;
              indentPixels = indentNumber * rootFontSize;
            } else if (indentText.endsWith('em')) indentPixels = indentNumber * fontSize;
            else indentPixels = indentNumber;
          }
          var indentEm = indentPixels / fontSize;
          return (isFinite(ratio) ? String(ratio) : '') + '|' + (isFinite(indentEm) ? String(indentEm) : '');
        })();
    """.trimIndent()

    private val APPLY_SCRIPT = """
        (function () {
          var id = 'papa-custom-paragraph-typography';
          var state = window.__papaCustomParagraphTypography || {};
          var old = document.getElementById(id);
          var line = '__PAPA_LINE_HEIGHT__';
          var indent = '__PAPA_TEXT_INDENT__';
          if (!line && !indent) {
            if (state.observer) state.observer.disconnect();
            try { delete window.__papaCustomParagraphTypography; } catch (ignored) {}
            if (old) old.remove();
            return 'unchanged';
          }
          var rules = [];
          if (line) rules.push('line-height:' + line + ' !important');
          if (indent) rules.push('text-indent:' + indent + 'em !important');
          var style = old || document.createElement('style');
          style.id = id;
          style.textContent = 'html body p:not(#papa-custom-paragraph-typography):not(#papa-custom-paragraph-typography) {' + rules.join(';') + '}';
          var parent = document.head || document.documentElement;
          function keepLast() {
            if (style.parentNode !== parent || style !== parent.lastElementChild) {
              if (style.parentNode) style.parentNode.removeChild(style);
              parent.appendChild(style);
            }
          }
          keepLast();
          if (!state.observer && window.MutationObserver) {
            state.observer = new MutationObserver(keepLast);
            state.observer.observe(parent, { childList: true });
          }
          window.__papaCustomParagraphTypography = state;
          return 'applied';
        })();
    """.trimIndent()
}
