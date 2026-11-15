package com.chmouel.liseur.reader.progress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
class WholeBookPageIndexStore(resourceCount: Int) {
    private val _index = MutableStateFlow(WholeBookPageIndex.empty(resourceCount))
    val index: StateFlow<WholeBookPageIndex> = _index.asStateFlow()

    private val _currentPage = MutableStateFlow<CurrentResourcePage?>(null)
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
        recordResource(resourceIndex, totalPages)
        _currentPage.value = CurrentResourcePage(
            resourceIndex = resourceIndex,
            pageInResource = pageIndexZeroBased + 1,
            totalPagesInResource = totalPages,
        )
    }

    @Synchronized
    fun reset(resourceCount: Int) {
        _index.value = WholeBookPageIndex.empty(resourceCount)
        _currentPage.value = null
    }
}
