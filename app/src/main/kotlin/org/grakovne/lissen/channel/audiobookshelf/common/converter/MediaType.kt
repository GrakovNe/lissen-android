package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.domain.LibraryType

/** Whatever is not a podcast is a book, in both directions. */
internal fun String.toLibraryType(): LibraryType =
  when (this) {
    "podcast" -> LibraryType.PODCAST
    else -> LibraryType.LIBRARY
  }

internal fun LibraryType.toMediaType(): String =
  when (this) {
    LibraryType.PODCAST -> "podcast"
    LibraryType.LIBRARY -> "book"
  }
