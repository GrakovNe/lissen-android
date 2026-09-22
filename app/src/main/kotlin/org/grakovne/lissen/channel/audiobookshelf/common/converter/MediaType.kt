package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.domain.LibraryType

internal fun String.toLibraryType(): LibraryType =
  when (this) {
    "podcast" -> LibraryType.PODCAST
    "book" -> LibraryType.LIBRARY
    else -> LibraryType.UNKNOWN
  }

/** The server's media type of a session; whatever is not a podcast is a book to it. */
internal fun LibraryType.toMediaType(): String =
  when (this) {
    LibraryType.PODCAST -> "podcast"
    LibraryType.LIBRARY, LibraryType.UNKNOWN -> "book"
  }
