package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep

/** A playlist as the cache knows it: the rows of its cached members, [coverItemIds] comma-joined by SQLite. */
@Keep
data class PlaylistGroupEntry(
  val id: String,
  val name: String,
  val itemCount: Int,
  val coverItemIds: String,
)
