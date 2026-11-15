package com.chmouel.liseur.reader.progress

/**
 * Exact page counts reported by Readium for individual reading-order resources.
 *
 * A whole-book total is available only when every resource in the paginated
 * reading order has been measured. Partial data is useful for a prefix page
 * number, but is never extrapolated to an unmeasured resource.
 */
data class WholeBookPageIndex(
    val resourceCount: Int,
    val pagesByResource: Map<Int, Int> = emptyMap(),
) {
    init {
        require(resourceCount >= 0)
        require(pagesByResource.all { (index, pages) ->
            index in 0 until resourceCount && pages > 0
        })
    }

    val isComplete: Boolean
        get() = pagesByResource.size == resourceCount &&
            (0 until resourceCount).all(pagesByResource::containsKey)

    /** Exact total, and null until every resource has a measured page count. */
    val totalPages: Int?
        get() = if (isComplete) pagesByResource.values.sum() else null

    fun recording(resourceIndex: Int, totalPages: Int): WholeBookPageIndex {
        if (resourceIndex !in 0 until resourceCount || totalPages <= 0) return this
        if (pagesByResource[resourceIndex] == totalPages) return this
        return copy(pagesByResource = pagesByResource + (resourceIndex to totalPages))
    }

    /**
     * Returns an exact page number when this resource and every preceding
     * resource have been measured. [pageInResource] is one-based.
     */
    fun pageOfBook(resourceIndex: Int, pageInResource: Int): Int? {
        val resourcePages = pagesByResource[resourceIndex] ?: return null
        if (resourceIndex !in 0 until resourceCount ||
            pageInResource !in 1..resourcePages
        ) return null
        val preceding = (0 until resourceIndex).map { pagesByResource[it] ?: return null }
        return preceding.sum() + pageInResource
    }

    /**
     * Exact page within a chapter whose reading-order resources are supplied
     * in order. Returns null if an earlier resource in that chapter is unknown.
     */
    fun pageInChapter(
        resourceIndex: Int,
        pageInResource: Int,
        chapterResourceIndices: List<Int>,
    ): Int? {
        if (resourceIndex !in chapterResourceIndices) return null
        val resourcePages = pagesByResource[resourceIndex] ?: return null
        if (pageInResource !in 1..resourcePages) return null
        val before = chapterResourceIndices
            .takeWhile { it != resourceIndex }
            .map { pagesByResource[it] ?: return null }
        return before.sum() + pageInResource
    }

    /** Exact chapter total, or null until all its resources have been measured. */
    fun pagesInChapter(chapterResourceIndices: List<Int>): Int? {
        if (chapterResourceIndices.any { it !in pagesByResource }) return null
        return chapterResourceIndices.sumOf { pagesByResource.getValue(it) }
    }

    companion object {
        fun empty(resourceCount: Int) = WholeBookPageIndex(resourceCount)
    }
}
