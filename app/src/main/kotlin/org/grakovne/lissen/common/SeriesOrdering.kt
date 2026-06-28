package org.grakovne.lissen.common

import org.grakovne.lissen.domain.Book

/**
 * Series index parsed out of the `"Series Name #3"` display string, or null when the book has no
 * sequence. Shared between series ordering and the per-row sequence label.
 */
fun Book.seriesSequence(): String? =
  series
    ?.substringAfterLast('#', "")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

/**
 * Orders books within a series by their numeric sequence (1, 2, 22 — not lexicographic 1, 2, 22).
 * Books without a parseable index are kept, in their original order, at the end.
 */
fun List<Book>.sortedBySeriesPosition(): List<Book> = sortedWith(compareBy(nullsLast()) { it.seriesSequence()?.toDoubleOrNull() })
