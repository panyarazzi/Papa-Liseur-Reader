package com.chmouel.liseur.reader.progress

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import java.util.concurrent.atomic.AtomicLong

/** A resource page count reported by either Readium navigator. */
data class ResourcePageMeasurement(
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
class WholeBookPageIndexStore(private val resourceCount: Int) {
    @Volatile
    var generation: Int = 0
        private set

    private val measurementSequence = AtomicLong(0)
    @Volatile
    var prepareResource: suspend (EpubNavigatorFragment) -> Boolean? = { null }

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
        _index.value = _index.value.recording(resourceIndex, totalPages)
    }

    @Synchronized
    fun onVisiblePageChanged(
        resourceIndex: Int,
        pageIndexZeroBased: Int,
        totalPages: Int,
    ) {
        if (totalPages <= 0 || pageIndexZeroBased !in 0 until totalPages) return
        _currentPage.value = CurrentResourcePage(
            resourceIndex = resourceIndex,
            pageInResource = pageIndexZeroBased + 1,
            totalPagesInResource = totalPages,
        )
    }

    fun recordMeasurement(href: String, resourceIndex: Int, totalPages: Int) {
        if (href.isBlank() || totalPages <= 0) return
        recordResource(resourceIndex, totalPages)
        publishMeasurement(href, totalPages)
    }

    fun publishMeasurement(href: String, totalPages: Int) {
        if (href.isBlank() || totalPages <= 0) return
        _measurements.tryEmit(
            ResourcePageMeasurement(href, totalPages, generation, measurementSequence.incrementAndGet()),
        )
    }

    @Synchronized
    fun reset(resourceCount: Int = this.resourceCount) {
        generation += 1
        _index.value = WholeBookPageIndex.empty(resourceCount)
        _currentPage.value = null
    }
}
