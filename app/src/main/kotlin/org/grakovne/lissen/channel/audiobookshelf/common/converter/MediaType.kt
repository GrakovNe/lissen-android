package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.domain.LibraryType

/** Whatever the server does not call a podcast is a book to the app. */
internal fun String.toLibraryType(): LibraryType =
  when (this) {
    "podcast" -> LibraryType.PODCAST
    else -> LibraryType.LIBRARY
  }

/** The server's media type of a session; whatever is not a podcast is a book to it. */
internal fun LibraryType.toMediaType(): String =
  when (this) {
    LibraryType.PODCAST -> "podcast"
    LibraryType.LIBRARY -> "book"
  }
