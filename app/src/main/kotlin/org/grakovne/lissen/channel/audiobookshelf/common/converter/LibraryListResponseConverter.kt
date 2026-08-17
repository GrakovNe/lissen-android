package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryResponse
import org.grakovne.lissen.domain.FilterData
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.NamedId
import javax.inject.Inject
import javax.inject.Singleton

private fun String.toLibraryType() =
  when (this) {
    "podcast" -> LibraryType.PODCAST
    "book" -> LibraryType.LIBRARY
    else -> LibraryType.UNKNOWN
  }

@Singleton
class LibraryListResponseConverter
  @Inject
  constructor() {
    fun apply(response: List<LibraryItemResponse>): List<Library> = response.map { Library(it.id, it.name, it.mediaType.toLibraryType()) }
  }

@Singleton
class LibraryResponseConverter
  @Inject
  constructor() {
    fun apply(response: LibraryResponse): Library =
      Library(
        id = response.library.id,
        title = response.library.name,
        type = response.library.mediaType.toLibraryType(),
        filters =
          FilterData(
            authors = response.filterdata.authors.map { NamedId(it.id, it.name) },
            genres = response.filterdata.genres,
            tags = response.filterdata.tags,
            series = response.filterdata.series.map { NamedId(it.id, it.name) },
          ),
      )
  }
