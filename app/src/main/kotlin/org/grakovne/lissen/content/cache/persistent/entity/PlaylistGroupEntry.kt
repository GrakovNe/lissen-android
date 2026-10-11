package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep

@Keep
data class PlaylistGroupEntry(
  val id: String,
  val name: String,
)
