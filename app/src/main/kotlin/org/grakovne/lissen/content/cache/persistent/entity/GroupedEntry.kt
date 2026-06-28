package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep

@Keep
data class GroupedEntry(
  val groupKey: String,
  val seriesId: String?,
  val bookCount: Int,
)
