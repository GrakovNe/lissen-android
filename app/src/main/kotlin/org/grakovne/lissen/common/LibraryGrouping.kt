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

// the grouping is picked once and kept for every library, but a podcast library has only playlists to group by
fun LibraryGrouping.supportedBy(libraryType: LibraryType?): LibraryGrouping =
  when (libraryType) {
    LibraryType.PODCAST -> takeIf { it == LibraryGrouping.PLAYLIST } ?: LibraryGrouping.NONE
    else -> this
  }
