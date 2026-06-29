package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorsResponse
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PagedItems
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryAuthorsResponseConverter
  @Inject
  constructor() {
    fun apply(response: LibraryAuthorsResponse): PagedItems<LibraryEntry> =
      response
        .results
        .map {
          LibraryEntry.AuthorEntry(
            id = it.id,
            name = it.name,
            bookCount = it.numBooks ?: 0,
          )
        }.let {
          PagedItems(
            items = it,
            currentPage = response.page,
            totalItems = response.total,
          )
        }
  }
