package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep

@Keep
data class NameGroupEntry(
  val name: String,
  val bookCount: Int,
)
