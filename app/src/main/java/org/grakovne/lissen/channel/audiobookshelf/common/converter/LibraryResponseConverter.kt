package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryResponse
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.domain.Library
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryResponseConverter
  @Inject
  constructor() {
    fun apply(response: LibraryResponse): List<Library> =
      response
        .libraries
        .map {
          it
            .mediaType
            .toLibraryType()
            .let { type -> Library(it.id, it.name, type) }
        }

    private fun String.toLibraryType() =
      when (this) {
        "podcast" -> LibraryType.PODCAST
        "book" -> LibraryType.LIBRARY
        else -> LibraryType.UNKNOWN
      }
  }
