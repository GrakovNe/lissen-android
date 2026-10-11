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

// podcasts and libraries of unknown type group only by playlists
fun LibraryGrouping.isAvailableIn(libraryType: LibraryType?): Boolean =
  libraryType == LibraryType.LIBRARY || this == LibraryGrouping.NONE || this == LibraryGrouping.PLAYLIST

fun LibraryGrouping.effectiveIn(libraryType: LibraryType?): LibraryGrouping =
  takeIf { it.isAvailableIn(libraryType) } ?: LibraryGrouping.NONE
