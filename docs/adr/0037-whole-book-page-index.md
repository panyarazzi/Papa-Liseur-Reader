# 37. Whole-book page index from Readium pagination callbacks

Date: 2026-11-15

Status: accepted

Supersedes the implementation decision in [36. The footer counts screens](0036-the-footer-counts-screens.md).

## Context

A page number must count actual page turns under the current viewport and effective
typography. Readium's stable Positions are useful for locators, bookmarks, sync,
scrubbing and restoration, but they are not pages. Measuring WebView scroll geometry
and extrapolating the unmeasured part of the book also does not produce a true
whole-book page count.

Readium's EPUB navigator exposes `PaginationListener.onPageChanged(pageIndex,
totalPages, locator)`. Its `totalPages` is exact for the current reading-order
resource at the navigator's current layout. The page index is zero-based and is
normalized to one-based numbering by the app.

## Decision

The reflowable paginated reader uses Readium's pagination callback as the only
source of screen-page facts.

A separate, invisible `EpubNavigatorFragment` measures the publication's reading
order sequentially. It is hosted in its own child FragmentManager and uses the same
viewport, EPUB preferences, user-font declarations, and reader configuration as the
visible navigator. Its navigation never changes the locator or page shown to the
reader. Live preference changes and viewport changes invalidate the old index and
start a new measurement generation.

The visible navigator reports the current resource index and one-based page through
its own `PaginationListener`. The measurement navigator records the exact
`totalPages` for each reading-order resource. A whole-book page number is the sum
of all preceding resource counts plus the current page.

The total for the book is available only after every reading-order resource has a
valid count. A page-of-book number is available only when the current resource and
all preceding resources have counts. Chapter totals and chapter page numbers are
available only when every resource needed for that chapter calculation has been
measured. No extrapolation, proportional scaling, or substitution of stable
Positions is allowed.

## Invariants

- `pageIndex + 1` is the one-based page within the current resource.
- `pageOfBook = sum(previous resource page counts) + pageInResource`.
- `totalPagesOfBook` is the sum of every resource count, only when the index is
  complete.
- Chapter pages aggregate the page counts of all resources belonging to the chapter,
  including split chapters.
- Repeated hrefs remain distinct reading-order occurrences in the index. The
  scanner binds each callback to the reading-order entry it is currently measuring;
  if Readium resolves a repeated href to its first occurrence, the same XHTML
  resource's count is reused for that occurrence. The visible page still resolves
  through Readium locators and the BookPositions occurrence mapping.
- Fixed-layout pagination and scrolling behavior are not redefined by this index.
  Scrolling has no page count to report.
- Stable Readium Positions remain the source for locator persistence, bookmarks,
  sync, go-to, scrubber and reading restoration.
- Highlight, note, bookmark and annotation records remain independent of the page
  index. Their original Locator JSON, selected excerpt, chapter label, note text and
  tint are not rewritten when the page index is measured or invalidated.
- The versioned ZIP backup continues to carry annotations in `annotations.json`.
  The derived page index is not exported or restored; it is rebuilt from Readium
  pagination for the current layout.
- Page counts are transient display data. They are not written into reading
  positions, synchronized, or used as bookmark identity.
- A stale callback from a previous layout generation must never populate the new
  index.

## Costs and safeguards

Measuring the whole book requires loading every reading-order resource in a second
navigator. It costs startup and WebView work, but it does not interrupt the visible
reader. A resource that fails to produce a valid Readium page callback remains
unmeasured; the app leaves dependent totals blank rather than inventing a count.
Measurement is local and can be repeated after typography or viewport changes.

## Consequences

The whole-book total is exact for the current layout once indexing completes. It
changes when typography or viewport geometry changes because the book has genuinely
reflowed. Until indexing completes, the interface shows only figures that can be
proved from the measured prefix or chapter resources; it never displays an estimated
denominator.

The old `SectionScreenProgress`, `BookScreenEstimate` and `FooterPages`
scroll-geometry/extrapolation path is retired. The footer and top progress use the
new index. Stable progress and reading-position restoration remain separate.
