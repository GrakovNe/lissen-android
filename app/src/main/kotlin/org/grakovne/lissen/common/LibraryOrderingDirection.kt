package org.grakovne.lissen.common

enum class LibraryOrderingDirection {
  ASCENDING,
  DESCENDING,
  ;

  val opposite: LibraryOrderingDirection
    get() = if (this == ASCENDING) DESCENDING else ASCENDING
}
