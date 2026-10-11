package org.grakovne.lissen.common

import org.grakovne.lissen.domain.LibraryType

enum class LibraryGrouping {
  NONE,
  SERIES,
  AUTHOR,
  GENRE,
  NARRATOR,
  PLAYLIST,
}

// a podcast library has only playlists to group by: any other choice shows it as a plain list
fun LibraryGrouping.supportedBy(libraryType: LibraryType?): LibraryGrouping =
  when (libraryType) {
    LibraryType.PODCAST -> takeIf { it == LibraryGrouping.PLAYLIST } ?: LibraryGrouping.NONE
    else -> this
  }
