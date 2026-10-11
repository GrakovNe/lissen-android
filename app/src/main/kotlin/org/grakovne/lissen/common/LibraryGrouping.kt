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

// a podcast library has only playlists to group by
fun LibraryGrouping.isAvailableIn(libraryType: LibraryType?): Boolean =
  libraryType != LibraryType.PODCAST || this == LibraryGrouping.NONE || this == LibraryGrouping.PLAYLIST

/** The grouping a library of [libraryType] shows for this choice: the choice itself, or a plain list. */
fun LibraryGrouping.effectiveIn(libraryType: LibraryType?): LibraryGrouping =
  takeIf { it.isAvailableIn(libraryType) } ?: LibraryGrouping.NONE
