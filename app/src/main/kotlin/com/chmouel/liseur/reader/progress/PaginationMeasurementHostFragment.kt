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
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication

/**
 * A second, invisible Readium navigator used only to measure each resource.
 *
 * It has its own child FragmentManager so its PaginationListener and page
 * navigation cannot interfere with the navigator the reader is using.
 * The host must be laid out at the same viewport size and with the same
 * preferences/configuration as the visible navigator.
 */
class PaginationMeasurementHostFragment(
    private val publication: Publication,
    private val navigatorFactory: FragmentFactory,
    private val pageIndexStore: WholeBookPageIndexStore,
    private val enabled: Boolean,
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
        isClickable = false
        isFocusable = false
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
        if (!enabled) return
        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
                as? EpubNavigatorFragment ?: return@launch
            scanReadingOrder(navigator)
        }
    }

    override fun onStop() {
        scanJob?.cancel()
        scanJob = null
        super.onStop()
    }

    private suspend fun scanReadingOrder(navigator: EpubNavigatorFragment) {
        val measurementGeneration = pageIndexStore.generation
        publication.readingOrder.forEachIndexed { index, link ->
            if (pageIndexStore.index.value.pagesByResource.containsKey(index)) {
                return@forEachIndexed
            }

            val locator = publication.locatorFromLink(link) ?: return@forEachIndexed
            val href = locator.href.toString().substringBefore('#')
            if (!navigator.go(locator)) return@forEachIndexed

            // Readium emits this after the resource has loaded and its page
            // count has settled. The bounded wait prevents one malformed
            // resource from holding up the rest of the book indefinitely.
            val measured = withTimeoutOrNull(RESOURCE_MEASUREMENT_TIMEOUT_MS) {
                pageIndexStore.measurements.first { event ->
                    event.generation == measurementGeneration &&
                        event.href.substringBefore('#') == href && event.totalPages > 0
                }
            } ?: return@forEachIndexed

            pageIndexStore.recordResource(index, measured.totalPages)
        }
    }

    companion object {
        private const val NAVIGATOR_TAG = "whole-book-page-measurement-navigator"
        private const val RESOURCE_MEASUREMENT_TIMEOUT_MS = 20_000L
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
