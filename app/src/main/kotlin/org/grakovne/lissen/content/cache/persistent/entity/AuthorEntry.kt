package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep

@Keep
data class AuthorEntry(
  val author: String,
  val bookCount: Int,
)
