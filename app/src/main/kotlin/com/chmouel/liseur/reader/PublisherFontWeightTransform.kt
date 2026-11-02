package com.chmouel.liseur.reader

import kotlin.math.roundToInt

/**
 * Applies Readium's font-weight preference to publisher declarations which
 * explicitly say "normal".
 *
 * Readium 3.4 places the requested weight on the document root. That reaches
 * text which inherits its weight, but loses to publisher rules such as
 * "p { font-weight: normal }". We mirror Readest's stylesheet transform at
 * runtime: normal declarations in ordinary style rules become a variable,
 * while bold/italic rules and @font-face descriptors remain untouched.
 *
 * The EPUB package is never rewritten; only the live document's CSSOM changes.
 */
internal object PublisherFontWeightTransform {

    fun targetWeight(multiplier: Double?): Int {
        val safeMultiplier = multiplier?.takeIf { it.isFinite() && it >= 0.0 } ?: 1.0
        return (400.0 * safeMultiplier).roundToInt().coerceIn(1, 1000)
    }

    fun script(multiplier: Double?): String =
        SCRIPT.replace("__PAPA_FONT_WEIGHT__", targetWeight(multiplier).toString())

    private val SCRIPT = """
        (function () {
          var root = document.documentElement;
          if (!root) return "no-document";

          var state = window.__papaPublisherFontWeight;
          if (!state) {
            state = { observer: null, pending: false, apply: null, schedule: null };
            window.__papaPublisherFontWeight = state;
          }

          var target = __PAPA_FONT_WEIGHT__;
          root.style.setProperty("--papa-reader-font-weight", String(target));

          function rewriteDeclaration(style) {
            if (!style || !style.getPropertyValue) return false;
            var value = style.getPropertyValue("font-weight");
            if (!value || value.trim().toLowerCase() !== "normal") return false;
            var priority = style.getPropertyPriority("font-weight");
            style.setProperty(
              "font-weight",
              "var(--papa-reader-font-weight, 400)",
              priority
            );
            return true;
          }

          function rewriteRules(rules) {
            if (!rules) return false;
            var changed = false;
            for (var i = 0; i < rules.length; i++) {
              var rule = rules[i];
              if (!rule) continue;
              try {
                // CSSStyleRule only. In particular, do not rewrite
                // @font-face descriptors: var() is not a valid font-face
                // font-weight descriptor and would break embedded fonts.
                if (rule.type === 1 && rule.style) {
                  changed = rewriteDeclaration(rule.style) || changed;
                }
                // Recurse into @media, @supports, @layer and @import rules.
                if (rule.cssRules) {
                  changed = rewriteRules(rule.cssRules) || changed;
                }
                if (rule.styleSheet) {
                  changed = rewriteSheet(rule.styleSheet) || changed;
                }
              } catch (ignored) {
                // One inaccessible or malformed nested rule must not stop
                // the remaining publisher stylesheets from being handled.
              }
            }
            return changed;
          }

          function rewriteSheet(sheet) {
            try {
              return !!(sheet && sheet.cssRules && rewriteRules(sheet.cssRules));
            } catch (ignored) {
              // Cross-origin or browser-protected stylesheets are left alone.
              return false;
            }
          }

          state.apply = function () {
            var sheets = document.styleSheets || [];
            for (var i = 0; i < sheets.length; i++) rewriteSheet(sheets[i]);

            // XHTML can carry declarations directly on elements as well as
            // in linked or embedded stylesheets.
            var inlineStyles = document.querySelectorAll("[style]");
            for (var j = 0; j < inlineStyles.length; j++) {
              rewriteDeclaration(inlineStyles[j].style);
            }
          };

          state.schedule = function () {
            if (state.pending) return;
            state.pending = true;
            var run = function () {
              state.pending = false;
              if (state.apply) state.apply();
            };
            if (window.requestAnimationFrame) {
              window.requestAnimationFrame(run);
            } else {
              window.setTimeout(run, 0);
            }
          };

          state.apply();

          // Catch stylesheets and inline styles added after the first pass.
          if (!state.observer && window.MutationObserver) {
            state.observer = new MutationObserver(function () {
              state.schedule();
            });
            state.observer.observe(root, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: ["style", "href", "rel"]
            });
            document.addEventListener("load", state.schedule, true);
          }

          return "applied";
        })();
    """.trimIndent()
}
