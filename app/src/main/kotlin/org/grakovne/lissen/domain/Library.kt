package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
data class Library(
  val id: String,
  val title: String,
  val type: LibraryType,
  val filters: FilterData? = null,
)

data class NamedId(
  val id: String,
  val name: String,
)

data class FilterData(
  val authors: List<NamedId>,
  val series: List<NamedId>,
  val genres: List<String>,
  val tags: List<String>,
)