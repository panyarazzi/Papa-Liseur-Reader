package com.chmouel.liseur.reader.progress

import android.util.Log
import com.chmouel.liseur.reader.ResourceAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

/**
 * Startup barrier for publisher fonts and the layout they produce.
 *
 * Readium can create the first pagination before WebView has finished
 * loading the document's @font-face resources. FontFaceSet.ready is the
 * browser signal for completion of the fonts currently needed by the
 * document; a stable layout signature is then required separately because
 * Readium's pagination is downstream from WebView layout.
 *
 * A failed font is reported as a settled error, not mistaken for success.
 * A timeout is bounded and visible in diagnostics so a broken EPUB cannot
 * hold the reader closed forever.
 */
internal object PublisherFontReadiness {
    const val DEFAULT_TIMEOUT_MS = 8_000L
    private const val POLL_INTERVAL_MS = 100L
    private const val REQUIRED_STABLE_READS = 2

    data class Snapshot(
        val supported: Boolean,
        val ready: Boolean,
        val fontSetStatus: String,
        val pendingStylesheets: Int,
        val fontFaces: Int,
        val loadedFaces: Int,
        val loadingFaces: Int,
        val failedFaces: Int,
        val layoutSignature: String? = null,
        val timedOut: Boolean = false,
        val status: String = "WAITING",
    )

    @OptIn(ExperimentalReadiumApi::class)
    suspend fun await(
        navigator: EpubNavigatorFragment,
        locator: Locator,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Snapshot {
        var lastSnapshot: Snapshot? = null
        var previousSignature: String? = null
        var stableReads = 0
        var outcome: Snapshot? = null

        val settled = withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
            while (outcome == null) {
                val currentHref = navigator.currentLocator.value.href.toString()
                if (!ResourceAddress.shows(currentHref, locator.href.toString())) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val raw = try {
                    navigator.evaluateJavascript(script())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.i(
                        "ReaderOpenTrace",
                        "open-font-gate-probe exception=${error.javaClass.simpleName} href=${locator.href}",
                    )
                    delay(POLL_INTERVAL_MS)
                    continue
                }
                val snapshot = parse(raw)
                if (snapshot == null) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }
                lastSnapshot = snapshot
                if (!snapshot.supported) {
                    outcome = snapshot.copy(status = "UNSUPPORTED")
                    continue
                }

                if (snapshot.ready) {
                    val signature = ExactLocatorAnchor.layoutSignature(navigator)
                    if (signature != null && signature == previousSignature) {
                        stableReads++
                    } else {
                        stableReads = 0
                    }
                    previousSignature = signature
                    if (stableReads >= REQUIRED_STABLE_READS) {
                        outcome = snapshot.copy(
                            layoutSignature = signature,
                            status = if (snapshot.failedFaces > 0) "READY_WITH_FONT_ERRORS" else "READY",
                        )
                    }
                } else {
                    previousSignature = null
                    stableReads = 0
                }
                if (outcome == null) delay(POLL_INTERVAL_MS)
            }
            outcome
        }

        return settled ?: (lastSnapshot ?: Snapshot(
            supported = false,
            ready = false,
            fontSetStatus = "unknown",
            pendingStylesheets = -1,
            fontFaces = 0,
            loadedFaces = 0,
            loadingFaces = 0,
            failedFaces = 0,
        )).copy(
            ready = false,
            status = "TIMEOUT",
            timedOut = true,
        )
    }

    internal fun parse(result: String?): Snapshot? {
        if (result.isNullOrBlank() || result == "null") return null
        val decoded = runCatching { JSONTokener(result).nextValue() }.getOrNull()
        val json = when (decoded) {
            is JSONObject -> decoded
            is String -> runCatching { JSONObject(decoded) }.getOrNull()
            else -> null
        } ?: return null
        val supported = json.optBoolean("supported", false)
        return Snapshot(
            supported = supported,
            ready = json.optBoolean("ready", false),
            fontSetStatus = json.optString("fontSetStatus", "unknown"),
            pendingStylesheets = json.optInt("pendingStylesheets", -1),
            fontFaces = json.optInt("fontFaces", 0),
            loadedFaces = json.optInt("loadedFaces", 0),
            loadingFaces = json.optInt("loadingFaces", 0),
            failedFaces = json.optInt("failedFaces", 0),
            status = if (supported) "WAITING" else "UNSUPPORTED",
        )
    }

    internal fun script(): String = SCRIPT

    private val SCRIPT = """
        (() => {
          const fontSet = document.fonts;
          if (!fontSet || !fontSet.ready) {
            return JSON.stringify({ supported: false });
          }

          // FontFaceSet.ready changes when a new batch of fonts starts
          // loading. Reattach to the current promise rather than trusting
          // an earlier "ready" event from before a stylesheet was applied.
          let state = window.__papaPublisherFontReadiness;
          if (!state) {
            state = { promise: null, ready: false };
            window.__papaPublisherFontReadiness = state;
          }
          const promise = fontSet.ready;
          if (state.promise !== promise) {
            state.promise = promise;
            state.ready = false;
            promise.then(() => {
              if (state.promise === promise) state.ready = true;
            });
          }

          const pendingStylesheets = Array.from(
            document.querySelectorAll('link[rel~="stylesheet"]')
          ).filter(link => !link.disabled && !link.sheet).length;
          const faces = Array.from(fontSet);
          const loadingFaces = faces.filter(face => face.status === "loading").length;
          const loadedFaces = faces.filter(face => face.status === "loaded").length;
          const failedFaces = faces.filter(face => face.status === "error").length;
          const fontSetStatus = fontSet.status;

          return JSON.stringify({
            supported: true,
            ready: state.ready && fontSetStatus === "loaded" &&
              pendingStylesheets === 0 && loadingFaces === 0,
            fontSetStatus,
            pendingStylesheets,
            fontFaces: faces.length,
            loadedFaces,
            loadingFaces,
            failedFaces
          });
        })()
    """.trimIndent()
}
