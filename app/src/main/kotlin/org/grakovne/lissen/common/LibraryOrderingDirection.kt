package org.grakovne.lissen.common

import androidx.annotation.Keep

/** A tap on an option picks it ascending; a tap on the picked one turns it around. */
@Keep
fun LibraryOrderingDirection.nextOnTap(selected: Boolean): LibraryOrderingDirection =
  when {
    !selected -> LibraryOrderingDirection.ASCENDING
    this == LibraryOrderingDirection.ASCENDING -> LibraryOrderingDirection.DESCENDING
    else -> LibraryOrderingDirection.ASCENDING
  }

enum class LibraryOrderingDirection {
  ASCENDING,
  DESCENDING,
}
