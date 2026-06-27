package org.grakovne.lissen.domain

import androidx.annotation.Keep

/**
 * An item shown in the library list. When series grouping is enabled the list mixes
 * standalone [BookEntry] rows with collapsed [SeriesEntry] rows; otherwise every row is a [BookEntry].
 */
@Keep
sealed interface LibraryEntry {
  @Keep
  data class BookEntry(
    val book: Book,
  ) : LibraryEntry

  @Keep
  data class SeriesEntry(
    val id: String,
    val title: String,
    val author: String?,
    val bookCount: Int,
    val coverItemIds: List<String>,
  ) : LibraryEntry
}
