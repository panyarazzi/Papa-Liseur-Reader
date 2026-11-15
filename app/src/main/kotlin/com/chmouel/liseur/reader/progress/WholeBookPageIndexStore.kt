package com.chmouel.liseur.reader.progress

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import java.util.concurrent.atomic.AtomicLong

/** A resource page count reported by the isolated measurement navigator. */
data class ResourcePageMeasurement(
    val resourceIndex: Int,
    val href: String,
    val totalPages: Int,
    val generation: Int,
    val sequence: Long,
)

/** The exact page currently reported by the visible Readium navigator. */
data class CurrentResourcePage(
    val resourceIndex: Int,
    val pageInResource: Int,
    val totalPagesInResource: Int,
)

/**
 * Shared state between the visible navigator, the background measurement
 * navigator and reader chrome. It deliberately contains no scroll geometry.
 */
class WholeBookPageIndexStore(
    private val resourceCount: Int,
    private val onDiagnostic: (String, Map<String, String>) -> Unit = { _, _ -> },
) {
    @Volatile
    var generation: Int = 0
        private set

    private val _generationFlow = MutableStateFlow(0)
    val generationFlow: StateFlow<Int> = _generationFlow.asStateFlow()

    // A replacement host waits for the previous host's cancelled scan to
    // release this lock. Two measurement navigators must never race over
    // the store's single expectedMeasurement slot.
    internal val scannerMutex = Mutex()

    private val measurementSequence = AtomicLong(0)
    @Volatile
    private var expectedMeasurement: Pair<Int, String>? = null

    @Volatile
    var prepareResource: (suspend (EpubNavigatorFragment) -> Boolean?)? = null

    private val _index = MutableStateFlow(WholeBookPageIndex.empty(resourceCount))
    val index: StateFlow<WholeBookPageIndex> = _index.asStateFlow()

    private val _currentPage = MutableStateFlow<CurrentResourcePage?>(null)
    private val _measurements = MutableSharedFlow<ResourcePageMeasurement>(
        replay = 1,
        extraBufferCapacity = 32,
    )
    val measurements: SharedFlow<ResourcePageMeasurement> = _measurements
    val currentPage: StateFlow<CurrentResourcePage?> = _currentPage.asStateFlow()

    @Synchronized
    fun recordResource(resourceIndex: Int, totalPages: Int) {
        val previous = _index.value
        val updated = previous.recording(resourceIndex, totalPages)
        if (updated == previous) return
        _index.value = updated
        onDiagnostic(
            "pagination.resource_measured",
            mapOf(
                "resourceIndex" to resourceIndex.toString(),
                "totalPages" to totalPages.toString(),
                "generation" to generation.toString(),
            ),
        )
        if (updated.isComplete) {
            onDiagnostic(
                "pagination.index_complete",
                mapOf(
                    "resourceCount" to updated.resourceCount.toString(),
                    "totalPages" to (updated.totalPages ?: 0).toString(),
                    "generation" to generation.toString(),
                ),
            )
        }
    }

    @Synchronized
    fun onVisiblePageChanged(
        resourceIndex: Int,
        pageIndexZeroBased: Int,
        totalPages: Int,
    ) {
        if (resourceIndex !in 0 until resourceCount || totalPages <= 0 || pageIndexZeroBased !in 0 until totalPages) return
        _currentPage.value = CurrentResourcePage(
            resourceIndex = resourceIndex,
            pageInResource = pageIndexZeroBased + 1,
            totalPagesInResource = totalPages,
        )
        onDiagnostic(
            "pagination.visible_page",
            mapOf(
                "resourceIndex" to resourceIndex.toString(),
                "pageIndexZeroBased" to pageIndexZeroBased.toString(),
                "totalPages" to totalPages.toString(),
                "generation" to generation.toString(),
            ),
        )
    }

    fun recordScannerEvent(event: String, attributes: Map<String, String> = emptyMap()) {
        onDiagnostic(event, attributes + ("generation" to generation.toString()))
    }

    /** Latest emitted measurement sequence, used to reject replayed callbacks. */
    fun latestMeasurementSequence(): Long = measurementSequence.get()

    /** The resource the isolated navigator is currently being asked to measure. */
    @Synchronized
    fun expectMeasurement(resourceIndex: Int, href: String) {
        if (resourceIndex !in 0 until resourceCount || href.isBlank()) {
            expectedMeasurement = null
            return
        }
        expectedMeasurement = resourceIndex to href.substringBefore('#')
    }

    @Synchronized
    fun clearExpectedMeasurement(resourceIndex: Int) {
        if (expectedMeasurement?.first == resourceIndex) expectedMeasurement = null
    }

    /**
     * Associates a callback with the scanner's active target, not a second
     * href-to-resource lookup. This matters when an EPUB lists the same
     * XHTML href more than once in its Reading Order.
     */
    @Synchronized
    fun publishMeasurement(
        resourceIndex: Int?,
        href: String,
        totalPages: Int,
        callbackGeneration: Int = generation,
    ) {
        val normalizedHref = href.substringBefore('#')
        if (normalizedHref.isBlank() || totalPages <= 0) return
        if (callbackGeneration != generation) {
            onDiagnostic(
                "pagination.measurement_callback_rejected",
                mapOf(
                    "reason" to "stale_generation",
                    "callbackGeneration" to callbackGeneration.toString(),
                    "activeGeneration" to generation.toString(),
                    "href" to normalizedHref,
                ),
            )
            return
        }
        val expected = expectedMeasurement
        val targetIndex = when {
            expected != null && normalizedHref == expected.second -> expected.first
            expected != null -> return
            else -> resourceIndex ?: return
        }
        if (targetIndex !in 0 until this.resourceCount) return
        _measurements.tryEmit(
            ResourcePageMeasurement(
                resourceIndex = targetIndex,
                href = normalizedHref,
                totalPages = totalPages,
                generation = callbackGeneration,
                sequence = measurementSequence.incrementAndGet(),
            ),
        )
    }

    @Synchronized
    fun reset() {
        generation += 1
        _generationFlow.value = generation
        expectedMeasurement = null
        _index.value = WholeBookPageIndex.empty(resourceCount)
        _currentPage.value = null
        onDiagnostic(
            "pagination.index_invalidated",
            mapOf(
                "resourceCount" to this.resourceCount.toString(),
                "generation" to generation.toString(),
            ),
        )
    }
}
