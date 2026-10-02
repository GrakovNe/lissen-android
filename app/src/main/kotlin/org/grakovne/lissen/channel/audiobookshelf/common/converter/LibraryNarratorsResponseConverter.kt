package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryNarratorsResponse
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.page
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryNarratorsResponseConverter
  @Inject
  constructor() {
    // abs returns every narrator in one response, so the list is paged here
    fun apply(
      response: LibraryNarratorsResponse,
      pageSize: Int,
      pageNumber: Int,
    ): PagedItems<LibraryEntry> =
      response
        .narrators
        .map { LibraryEntry.NarratorEntry(id = it.name, name = it.name, bookCount = it.numBooks ?: 0) }
        .sortedBy { it.name.lowercase() }
        .page(pageSize, pageNumber)
  }
