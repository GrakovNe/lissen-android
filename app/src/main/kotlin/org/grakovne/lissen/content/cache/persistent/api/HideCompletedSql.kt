package org.grakovne.lissen.content.cache.persistent.api

import org.grakovne.lissen.domain.LibraryType

fun hideCompletedApplies(
  hideCompleted: Boolean,
  libraryType: LibraryType?,
): Boolean = hideCompleted && libraryType == LibraryType.LIBRARY

fun hideCompletedSql(
  apply: Boolean,
  idColumn: String,
): Pair<String, String> =
  when (apply) {
    true -> "LEFT JOIN media_progress mp ON mp.bookId = $idColumn" to "AND (mp.isFinished = 0 OR mp.isFinished IS NULL)"
    false -> "" to ""
  }
