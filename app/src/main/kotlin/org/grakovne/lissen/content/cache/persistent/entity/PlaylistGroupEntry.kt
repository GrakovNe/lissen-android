package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep

/** A playlist as the cache knows it: counted over the rows of its cached members. */
@Keep
data class PlaylistGroupEntry(
  val id: String,
  val name: String,
  val itemCount: Int,
)
