package org.grakovne.lissen.common

import org.grakovne.lissen.domain.Book

fun Book.seriesSequence(seriesName: String? = null): String? {
  val line = series ?: return null

  // a book in several series has a line like "Ryanverse #7, Jack Ryan #6"
  val sequence =
    when {
      seriesName.isNullOrBlank() -> line.substringAfterLast('#', "")
      else -> Regex("(?:^|, )${Regex.escape(seriesName.trim())} #([^,]+)").find(line)?.groupValues?.get(1)
    }

  return sequence?.trim()?.takeIf { it.isNotEmpty() }
}

fun List<Book>.sortedBySeriesPosition(seriesName: String? = null): List<Book> =
  sortedWith(compareBy(nullsLast()) { it.seriesSequence(seriesName)?.toDoubleOrNull() })
