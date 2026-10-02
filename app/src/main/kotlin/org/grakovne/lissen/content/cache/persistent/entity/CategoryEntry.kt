package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep

/** One row of an author, narrator or genre grouping of the cached library. */
@Keep
data class CategoryEntry(
  val name: String,
  val bookCount: Int,
)
