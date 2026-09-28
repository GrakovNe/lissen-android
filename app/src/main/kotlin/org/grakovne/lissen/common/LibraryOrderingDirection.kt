package org.grakovne.lissen.common

import androidx.annotation.Keep

@Keep
enum class LibraryOrderingDirection {
  ASCENDING,
  DESCENDING,
  ;

  val opposite: LibraryOrderingDirection
    get() =
      when (this) {
        ASCENDING -> DESCENDING
        DESCENDING -> ASCENDING
      }
}
