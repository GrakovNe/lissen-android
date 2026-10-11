package org.grakovne.lissen.domain

import androidx.annotation.Keep

/** A row of a playlist: a book, or the episode [episodeId] of the podcast [book]. */
@Keep
data class PlaylistItem(
  val book: Book,
  val episodeId: String? = null,
  val episodeTitle: String? = null,
)
