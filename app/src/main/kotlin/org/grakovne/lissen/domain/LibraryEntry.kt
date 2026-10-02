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

  @Keep
  data class AuthorEntry(
    val id: String,
    val name: String,
    val bookCount: Int,
  ) : LibraryEntry

  @Keep
  data class GenreEntry(
    val id: String,
    val name: String,
    val bookCount: Int,
  ) : LibraryEntry

  @Keep
  data class NarratorEntry(
    val id: String,
    val name: String,
    val bookCount: Int,
  ) : LibraryEntry
}

fun List<LibraryEntry>.page(
  pageSize: Int,
  pageNumber: Int,
): PagedItems<LibraryEntry> =
  PagedItems(
    items = drop(pageSize * pageNumber).take(pageSize),
    currentPage = pageNumber,
    totalItems = size,
  )

fun PagedItems<Book>.asLibraryEntries(): PagedItems<LibraryEntry> =
  PagedItems(
    items = items.map { LibraryEntry.BookEntry(it) },
    currentPage = currentPage,
    totalItems = totalItems,
  )
