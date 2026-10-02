package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryStatsResponse
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.page
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryStatsResponseConverter
  @Inject
  constructor() {
    // abs has no paged genre list: the stats endpoint is the only one with per-genre book counts, so it is paged here
    fun apply(
      response: LibraryStatsResponse,
      pageSize: Int,
      pageNumber: Int,
    ): PagedItems<LibraryEntry> =
      response
        .genresWithCount
        .map { LibraryEntry.GenreEntry(id = it.genre, name = it.genre, bookCount = it.count) }
        .sortedBy { it.name.lowercase() }
        .page(pageSize, pageNumber)
  }
