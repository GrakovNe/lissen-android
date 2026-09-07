package org.grakovne.lissen.common

import org.grakovne.lissen.domain.Book

fun Book.seriesSequence(): String? =
  series
    ?.substringAfterLast('#', "")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

fun List<Book>.sortedBySeriesPosition(): List<Book> = sortedWith(compareBy(nullsLast()) { it.seriesSequence()?.toDoubleOrNull() })
