package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.common.combineAuthors
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PagedItems
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryPageResponseConverter
  @Inject
  constructor() {
    fun apply(response: LibraryItemsResponse): PagedItems<Book> =
      response
        .results
        .mapNotNull { it.toBook() }
        .let {
          PagedItems(
            items = it,
            currentPage = response.page,
            totalItems = response.total,
          )
        }

    fun applyEntries(response: LibraryItemsResponse): PagedItems<LibraryEntry> =
      response
        .results
        .mapNotNull { item ->
          val collapsed = item.collapsedSeries

          when (collapsed) {
            null -> {
              item.toBook()?.let { LibraryEntry.BookEntry(it) }
            }

            else -> {
              LibraryEntry.SeriesEntry(
                id = collapsed.id,
                title = collapsed.name,
                author = combineAuthors(listOf(item.media.metadata.authorName)),
                bookCount = collapsed.numBooks ?: 0,
                coverItemIds =
                  collapsed
                    .libraryItemIds
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(item.id),
              )
            }
          }
        }.let {
          PagedItems(
            items = it,
            currentPage = response.page,
            totalItems = response.total,
          )
        }

    private fun LibraryItem.toBook(): Book? {
      val title = media.metadata.title ?: return null

      return Book(
        id = id,
        title = title,
        series = media.metadata.seriesName,
        subtitle = media.metadata.subtitle,
        author = media.metadata.authorName,
      )
    }
  }
