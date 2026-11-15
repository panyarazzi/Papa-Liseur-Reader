package com.chmouel.liseur.reader.progress

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val measurementSequence = AtomicLong(0)
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

    fun publishMeasurement(resourceIndex: Int, href: String, totalPages: Int) {
        if (resourceIndex !in 0 until resourceCount || href.isBlank() || totalPages <= 0) return
        _measurements.tryEmit(
            ResourcePageMeasurement(
                resourceIndex = resourceIndex,
                href = href,
                totalPages = totalPages,
                generation = generation,
                sequence = measurementSequence.incrementAndGet(),
            ),
        )
    }

    @Synchronized
    fun reset() {
        generation += 1
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
