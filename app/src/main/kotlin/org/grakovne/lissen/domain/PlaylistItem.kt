package org.grakovne.lissen.domain

import androidx.annotation.Keep

/** One row of a playlist: the book itself, or the episode [episodeId] of the podcast [book]. */
@Keep
data class PlaylistItem(
  val book: Book,
  val episodeId: String? = null,
  val episodeTitle: String? = null,
)
