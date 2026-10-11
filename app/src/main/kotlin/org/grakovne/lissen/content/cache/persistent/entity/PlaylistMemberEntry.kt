package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep

@Keep
data class PlaylistMemberEntry(
  val playlistId: String,
  val bookId: String,
  val episodeId: String?,
  val episodeTitle: String?,
  val position: Int,
)
