package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryItemResponse
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryResponseConverter
  @Inject
  constructor() {
    fun apply(response: List<LibraryItemResponse>): List<Library> =
      response
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
