package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
data class PagedItems<T>(
  val items: List<T>,
  val currentPage: Int,
)
