package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep

@Keep
data class Library(
  val id: String,
  val title: String,
  val type: LibraryType,
)
