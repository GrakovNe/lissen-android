package org.grakovne.lissen.domain

import androidx.annotation.Keep

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

fun PagedItems<Book>.asLibraryEntries(): PagedItems<LibraryEntry> =
  PagedItems(
    items = items.map { LibraryEntry.BookEntry(it) },
    currentPage = currentPage,
    totalItems = totalItems,
  )
