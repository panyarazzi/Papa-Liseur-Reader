package com.chmouel.liseur.reader.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication

/**
 * A second, transparent Readium navigator used only to measure each resource.
 *
 * It has its own child FragmentManager so its PaginationListener and page
 * navigation cannot interfere with the navigator the reader is using.
 * The host must remain VISIBLE to Android: Readium's WebView needs an active
 * rendering lifecycle to lay out documents and emit pagination callbacks.
 * Alpha zero keeps it visually absent while preserving that lifecycle.
 */
class PaginationMeasurementHostFragment(
    private val publication: Publication,
    private val navigatorFactory: FragmentFactory,
    private val pageIndexStore: WholeBookPageIndexStore,
) : Fragment() {

    private var containerId: Int = View.generateViewId()
    private var scanJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext()).apply {
        id = containerId
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // Do not use INVISIBLE/GONE here. Readium reports pagination from
        // its WebView after rendering; a non-visible host can leave the
        // measurement navigator attached but never produce page callbacks.
        // Keep it in the render tree at alpha zero. It is composed before
        // the visible navigator, so the reader remains the topmost surface.
        visibility = View.VISIBLE
        alpha = 0f
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.fragmentFactory = navigatorFactory
        if (childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) == null) {
            childFragmentManager.commitNow {
                setReorderingAllowed(true)
                add(containerId, EpubNavigatorFragment::class.java, null, NAVIGATOR_TAG)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            pageIndexStore.scannerMutex.withLock {
                val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
                    as? EpubNavigatorFragment ?: return@withLock
                scanReadingOrder(navigator)
            }
        }
    }

    override fun onStop() {
        scanJob?.cancel()
        scanJob = null
        super.onStop()
    }

    private suspend fun scanReadingOrder(navigator: EpubNavigatorFragment) {
        val measurementGeneration = pageIndexStore.generation
        pageIndexStore.recordScannerEvent(
            "pagination.scan_started",
            mapOf(
                "resourceCount" to publication.readingOrder.size.toString(),
                "scannerGeneration" to measurementGeneration.toString(),
            ),
        )
        publication.readingOrder.forEachIndexed { index, link ->
            if (pageIndexStore.index.value.pagesByResource.containsKey(index)) {
                return@forEachIndexed
            }

            val locator = publication.locatorFromLink(link) ?: run {
                pageIndexStore.recordScannerEvent(
                    "pagination.resource_locator_missing",
                    mapOf("resourceIndex" to index.toString()),
                )
                return@forEachIndexed
            }
            val href = locator.href.toString().substringBefore('#')
            pageIndexStore.expectMeasurement(index, href)
            // SharedFlow replays its last value. Require a callback emitted
            // after this navigation request so a repeated href cannot reuse
            // the previous resource's measurement.
            val sequenceBeforeNavigation = pageIndexStore.latestMeasurementSequence()
            if (!navigator.go(locator, animated = false)) {
                pageIndexStore.clearExpectedMeasurement(index)
                pageIndexStore.recordScannerEvent(
                    "pagination.resource_navigation_failed",
                    mapOf("resourceIndex" to index.toString()),
                )
                return@forEachIndexed
            }

            // Readium emits this after the resource has loaded and its page
            // count has settled. The bounded wait prevents one malformed
            // resource from holding up the rest of the book indefinitely.
            val initialMeasurement = withTimeoutOrNull(RESOURCE_MEASUREMENT_TIMEOUT_MS) {
                pageIndexStore.measurements.first { event ->
                    event.generation == measurementGeneration &&
                        event.sequence > sequenceBeforeNavigation &&
                        event.href.substringBefore('#') == href && event.totalPages > 0
                }
            } ?: run {
                pageIndexStore.clearExpectedMeasurement(index)
                pageIndexStore.recordScannerEvent(
                    "pagination.resource_callback_timeout",
                    mapOf("resourceIndex" to index.toString()),
                )
                return@forEachIndexed
            }

            pageIndexStore.clearExpectedMeasurement(index)

            // Apply the same runtime typography and page repairs as the
            // visible reader before accepting a resource's final count.
            var prepareResource = pageIndexStore.prepareResource
            for (attempt in 0 until PREPARE_CALLBACK_WAIT_ATTEMPTS) {
                if (prepareResource != null) break
                kotlinx.coroutines.delay(PREPARE_CALLBACK_POLL_MS)
                prepareResource = pageIndexStore.prepareResource
            }
            val layoutChanged = prepareResource?.invoke(navigator) ?: run {
                pageIndexStore.recordScannerEvent(
                    "pagination.resource_prepare_failed",
                    mapOf("resourceIndex" to index.toString()),
                )
                return@forEachIndexed
            }
            val measured = if (layoutChanged) {
                // Re-issue the same locator in the isolated navigator so
                // Readium publishes a fresh page callback after DOM/CSS
                // changes, even when the current page index did not move.
                pageIndexStore.expectMeasurement(index, href)
                if (!navigator.go(locator, animated = false)) {
                    pageIndexStore.clearExpectedMeasurement(index)
                    pageIndexStore.recordScannerEvent(
                        "pagination.resource_reflow_navigation_failed",
                        mapOf("resourceIndex" to index.toString()),
                    )
                    return@forEachIndexed
                }
                withTimeoutOrNull(RESOURCE_MEASUREMENT_TIMEOUT_MS) {
                    pageIndexStore.measurements.first { event ->
                        event.generation == measurementGeneration &&
                            event.href.substringBefore('#') == href &&
                            event.totalPages > 0 &&
                            event.sequence > initialMeasurement.sequence
                    }
                } ?: run {
                    pageIndexStore.clearExpectedMeasurement(index)
                    pageIndexStore.recordScannerEvent(
                        "pagination.resource_reflow_timeout",
                        mapOf("resourceIndex" to index.toString()),
                    )
                    return@forEachIndexed
                }
            } else {
                initialMeasurement
            }
            pageIndexStore.clearExpectedMeasurement(index)

            pageIndexStore.recordResource(index, measured.totalPages)
        }
        val index = pageIndexStore.index.value
        pageIndexStore.recordScannerEvent(
            "pagination.scan_finished",
            mapOf(
                "measuredResourceCount" to index.pagesByResource.size.toString(),
                "resourceCount" to index.resourceCount.toString(),
                "complete" to index.isComplete.toString(),
                "totalPages" to (index.totalPages?.toString() ?: "unknown"),
            ),
        )
    }

    companion object {
        private const val NAVIGATOR_TAG = "whole-book-page-measurement-navigator"
        private const val RESOURCE_MEASUREMENT_TIMEOUT_MS = 20_000L
        private const val PREPARE_CALLBACK_WAIT_ATTEMPTS = 100
        private const val PREPARE_CALLBACK_POLL_MS = 50L
    }
}

/** FragmentFactory for the host, keeping its captured publication state out of Bundles. */
class PaginationMeasurementHostFactory(
    private val create: () -> PaginationMeasurementHostFragment,
) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
        if (className == PaginationMeasurementHostFragment::class.java.name) {
            create()
        } else {
            super.instantiate(classLoader, className)
        }
}

/**
 * Routes Compose-created reader fragments to the host or the visible
 * navigator factory without changing the activity's normal fallback.
 */
class ReaderFragmentFactory(
    private val hostFactory: PaginationMeasurementHostFactory,
    private val navigatorFactory: FragmentFactory,
) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
        if (className == PaginationMeasurementHostFragment::class.java.name) {
            hostFactory.instantiate(classLoader, className)
        } else {
            navigatorFactory.instantiate(classLoader, className)
        }
}
