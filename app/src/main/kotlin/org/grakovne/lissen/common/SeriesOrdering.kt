package org.grakovne.lissen.common

import org.grakovne.lissen.domain.Book

private val SERIES_SEGMENT = Regex("""^(.+?)\s+#(\S+)$""")

fun Book.seriesSequence(seriesName: String? = null): String? {
  val seriesStr = this.series ?: return null

  fun parse(raw: String): Pair<String, String>? =
    SERIES_SEGMENT.matchEntire(raw.trim())?.let { m -> m.groupValues[1].trim() to m.groupValues[2] }

  val segments = seriesStr.split(", ").mapNotNull(::parse)

  val matched = segments.firstOrNull { it.first.equals(seriesName.orEmpty(), ignoreCase = true) }
  return (matched ?: segments.firstOrNull())?.second
}

fun List<Book>.sortedBySeriesPosition(seriesName: String? = null): List<Book> =
  sortedWith(compareBy(nullsLast()) { it.seriesSequence(seriesName)?.toDoubleOrNull() })
